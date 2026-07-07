package com.tourcrm.service;

import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.ClueStatsResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.PerformanceRowResponse;
import com.tourcrm.dto.UserRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class StatsQueryService {

    private final JdbcTemplate jdbcTemplate;
    private final CustomerClueRepository customerClueRepository;

    public StatsQueryService(JdbcTemplate jdbcTemplate, CustomerClueRepository customerClueRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.customerClueRepository = customerClueRepository;
    }

    public ClueStatsResponse queryStats(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String startDate,
            String endDate
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildManagementClueWhereSql(visibleUploaderCodes, visibleSalesCodes, startDate, endDate, params);
        StatsCounts counts = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) AS total_count,\n"
                        + "       SUM(CASE WHEN DATE(created_at_value) = CURRENT_DATE THEN 1 ELSE 0 END) AS today_count,\n"
                        + "       SUM(CASE WHEN repeat_demand = 1 THEN 1 ELSE 0 END) AS repeat_demand_count\n"
                        + "FROM crm_clues\n"
                        + "WHERE " + where,
                (rs, rowNum) -> new StatsCounts(
                        rs.getLong("total_count"),
                        rs.getLong("today_count"),
                        rs.getLong("repeat_demand_count")
                ),
                params.toArray());
        if (counts == null) {
            counts = new StatsCounts(0, 0, 0);
        }
        return new ClueStatsResponse(
                counts.totalCount(),
                counts.todayCount(),
                counts.repeatDemandCount(),
                Math.max(0, counts.totalCount() - counts.repeatDemandCount()),
                queryGroupedCounts(where, params, "status", true),
                queryGroupedCounts(where, params, "COALESCE(NULLIF(uploader, ''), uploader_employee_code, '未归属')", true),
                queryGroupedCounts(where + " AND assigned_sales_employee_code IS NOT NULL AND assigned_sales_employee_code <> ''",
                        params,
                        "COALESCE(NULLIF(assigned_sales, ''), assigned_sales_employee_code, '未归属')",
                        true)
        );
    }

    public PageResponse<ClueResponse> queryStatsDetailPage(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String type,
            String value,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildManagementClueWhereSql(visibleUploaderCodes, visibleSalesCodes, startDate, endDate, params);
        String normalizedType = StringUtils.hasText(type) ? type.trim().toUpperCase(Locale.ROOT) : "ALL";
        String normalizedValue = value == null ? "" : value.trim();
        if ("STATUS".equals(normalizedType) && StringUtils.hasText(normalizedValue)) {
            where += " AND status = ?";
            params.add(normalizedValue.toUpperCase(Locale.ROOT));
        } else if ("UPLOADER".equals(normalizedType) && StringUtils.hasText(normalizedValue)) {
            where += " AND (uploader = ? OR uploader_employee_code = ?)";
            params.add(normalizedValue);
            params.add(normalizedValue.toUpperCase(Locale.ROOT));
        } else if ("SALES".equals(normalizedType) && StringUtils.hasText(normalizedValue)) {
            where += " AND (assigned_sales = ? OR assigned_sales_employee_code = ?)";
            params.add(normalizedValue);
            params.add(normalizedValue.toUpperCase(Locale.ROOT));
        } else if ("REPEAT".equals(normalizedType)) {
            where += " AND repeat_demand = 1";
        }
        return customerClueRepository.queryCluePage(where, params, page, pageSize);
    }

    public List<PerformanceRowResponse> queryPerformanceRows(List<UserRecord> users, String startDate, String endDate) {
        if (users == null || users.isEmpty()) {
            return List.of();
        }
        List<String> operationCodes = users.stream()
                .filter(user -> !"SALES".equals(user.position()))
                .map(UserRecord::employeeCode)
                .toList();
        List<String> salesCodes = users.stream()
                .filter(user -> "SALES".equals(user.position()))
                .map(UserRecord::employeeCode)
                .toList();
        Map<String, PerformanceCounts> countsByEmployee = new LinkedHashMap<>();
        queryPerformanceCounts(operationCodes, false, startDate, endDate)
                .forEach(counts -> countsByEmployee.put(counts.employeeCode(), counts));
        queryPerformanceCounts(salesCodes, true, startDate, endDate)
                .forEach(counts -> countsByEmployee.put(counts.employeeCode(), counts));
        return users.stream()
                .map(user -> {
                    PerformanceCounts counts = countsByEmployee.getOrDefault(user.employeeCode(), PerformanceCounts.empty(user.employeeCode()));
                    return new PerformanceRowResponse(
                            user.employeeCode(),
                            user.name(),
                            user.role(),
                            user.position(),
                            user.leaderEmployeeCode(),
                            counts.totalCount(),
                            counts.todayCount(),
                            counts.repeatDemandCount(),
                            Math.max(0, counts.totalCount() - counts.repeatDemandCount()),
                            counts.dealedCount(),
                            counts.refundedCount(),
                            counts.landedCount(),
                            counts.invalidCount()
                    );
                })
                .toList();
    }

    public PerformanceRowResponse queryPerformanceRow(UserRecord user, String startDate, String endDate) {
        List<PerformanceRowResponse> rows = queryPerformanceRows(List.of(user), startDate, endDate);
        return rows.isEmpty()
                ? new PerformanceRowResponse(user.employeeCode(), user.name(), user.role(), user.position(), user.leaderEmployeeCode(), 0, 0, 0, 0, 0, 0, 0, 0)
                : rows.get(0);
    }

    private List<PerformanceCounts> queryPerformanceCounts(List<String> employeeCodes, boolean sales, String startDate, String endDate) {
        if (employeeCodes == null || employeeCodes.isEmpty()) {
            return List.of();
        }
        List<Object> params = new ArrayList<>();
        String employeeColumn = sales ? "assigned_sales_employee_code" : "uploader_employee_code";
        StringBuilder where = new StringBuilder("status <> 'DELETED'");
        where.append(" AND ");
        customerClueRepository.appendInClause(where, params, employeeColumn, employeeCodes);
        customerClueRepository.appendDateTimeRange(where, params, "created_at_value", startDate, endDate);
        String sql = "SELECT " + employeeColumn + " AS employee_code,\n"
                + "       COUNT(*) AS total_count,\n"
                + "       SUM(CASE WHEN DATE(created_at_value) = CURRENT_DATE THEN 1 ELSE 0 END) AS today_count,\n"
                + "       SUM(CASE WHEN repeat_demand = 1 THEN 1 ELSE 0 END) AS repeat_demand_count,\n"
                + "       SUM(CASE WHEN status = 'DEPOSIT_PAID' THEN 1 ELSE 0 END) AS dealed_count,\n"
                + "       SUM(CASE WHEN status = 'REFUNDED' THEN 1 ELSE 0 END) AS refunded_count,\n"
                + "       SUM(CASE WHEN status = 'LANDED' THEN 1 ELSE 0 END) AS landed_count,\n"
                + "       SUM(CASE WHEN status = 'INVALID' THEN 1 ELSE 0 END) AS invalid_count\n"
                + "FROM crm_clues\n"
                + "WHERE " + where + "\n"
                + "GROUP BY " + employeeColumn;
        return jdbcTemplate.query(sql,
                (rs, rowNum) -> new PerformanceCounts(
                        rs.getString("employee_code"),
                        rs.getLong("total_count"),
                        rs.getLong("today_count"),
                        rs.getLong("repeat_demand_count"),
                        rs.getLong("dealed_count"),
                        rs.getLong("refunded_count"),
                        rs.getLong("landed_count"),
                        rs.getLong("invalid_count")
                ),
                params.toArray());
    }

    private String buildManagementClueWhereSql(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String startDate,
            String endDate,
            List<Object> params
    ) {
        StringBuilder where = new StringBuilder("status <> 'DELETED'");
        boolean hasUploaderScope = visibleUploaderCodes != null;
        boolean hasSalesScope = visibleSalesCodes != null;
        if (hasUploaderScope || hasSalesScope) {
            where.append(" AND (");
            boolean appended = false;
            if (hasUploaderScope) {
                customerClueRepository.appendInClause(where, params, "uploader_employee_code", visibleUploaderCodes);
                appended = true;
            }
            if (hasSalesScope) {
                if (appended) {
                    where.append(" OR ");
                }
                customerClueRepository.appendInClause(where, params, "assigned_sales_employee_code", visibleSalesCodes);
            }
            where.append(")");
        }
        customerClueRepository.appendDateTimeRange(where, params, "created_at_value", startDate, endDate);
        return where.toString();
    }

    private Map<String, Long> queryGroupedCounts(String where, List<Object> params, String groupExpression, boolean countDesc) {
        String sql = "SELECT " + groupExpression + " AS group_key, COUNT(*) AS total_count\n"
                + "FROM crm_clues\n"
                + "WHERE " + where + "\n"
                + "GROUP BY " + groupExpression + "\n"
                + "ORDER BY " + (countDesc ? "total_count DESC, group_key" : "group_key");
        Map<String, Long> rows = new LinkedHashMap<>();
        jdbcTemplate.query(sql, rs -> {
            String key = rs.getString("group_key");
            if (StringUtils.hasText(key)) {
                rows.put(key, rs.getLong("total_count"));
            }
        }, params.toArray());
        return rows;
    }

    private record PerformanceCounts(
            String employeeCode,
            long totalCount,
            long todayCount,
            long repeatDemandCount,
            long dealedCount,
            long refundedCount,
            long landedCount,
            long invalidCount
    ) {
        private static PerformanceCounts empty(String employeeCode) {
            return new PerformanceCounts(employeeCode, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    private record StatsCounts(
            long totalCount,
            long todayCount,
            long repeatDemandCount
    ) {
    }
}
