package com.tourcrm.service;

import com.tourcrm.dto.AssignLogRecord;
import com.tourcrm.dto.AssignLogReportRow;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.ClueStatsResponse;
import com.tourcrm.dto.DealResponse;
import com.tourcrm.dto.FollowRecord;
import com.tourcrm.dto.OperationLogRecord;
import com.tourcrm.dto.OperationLogReportRow;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.PerformanceRowResponse;
import com.tourcrm.dto.StatusChangeRecord;
import com.tourcrm.dto.UserRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class DatabaseStore {

    private final JdbcTemplate jdbcTemplate;
    private final ClueImageRepository clueImageRepository;
    private final SequenceRepository sequenceRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public DatabaseStore(
            JdbcTemplate jdbcTemplate,
            ClueImageRepository clueImageRepository,
            SequenceRepository sequenceRepository,
            CustomerProfileRepository customerProfileRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.clueImageRepository = clueImageRepository;
        this.sequenceRepository = sequenceRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    public List<ClueResponse> findCluesByContactKey(String contactKey) {
        if (!StringUtils.hasText(contactKey)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                        SELECT customer_code, source_platform, add_method, contact_info, has_wechat_id, uploader, uploader_employee_code,
                               org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                               assigned_sales_employee_code, deposit_amount, remaining_balance, status_remark, refund_amount, refunded_at,
                               landing_at, landing_remark, created_at_text, updated_at_text
                        FROM crm_clues
                        WHERE contact_key = ? AND status <> 'DELETED'
                        ORDER BY created_at_value ASC, created_at_text ASC, customer_code ASC
                        """,
                (rs, rowNum) -> readClueRow(rs),
                contactKey);
    }

    public List<ClueResponse> findCluesByRootCustomerCode(String rootCustomerCode) {
        if (!StringUtils.hasText(rootCustomerCode)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                        SELECT customer_code, source_platform, add_method, contact_info, has_wechat_id, uploader, uploader_employee_code,
                               org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                               assigned_sales_employee_code, deposit_amount, remaining_balance, status_remark, refund_amount, refunded_at,
                               landing_at, landing_remark, created_at_text, updated_at_text
                        FROM crm_clues
                        WHERE status <> 'DELETED'
                          AND (customer_code = ? OR original_customer_code = ?)
                        ORDER BY demand_sequence ASC, created_at_value ASC, created_at_text ASC, customer_code ASC
                        """,
                (rs, rowNum) -> readClueRow(rs),
                rootCustomerCode,
                rootCustomerCode);
    }

    public Optional<ClueResponse> findClueByCustomerCode(String customerCode) {
        if (!StringUtils.hasText(customerCode)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT customer_code, source_platform, add_method, contact_info, has_wechat_id, uploader, uploader_employee_code,
                                   org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                                   assigned_sales_employee_code, deposit_amount, remaining_balance, status_remark, refund_amount, refunded_at,
                                   landing_at, landing_remark, created_at_text, updated_at_text
                            FROM crm_clues
                            WHERE customer_code = ?
                            """,
                    (rs, rowNum) -> readClueRow(rs),
                    customerCode));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public Optional<String> findRootCustomerCodeByContactKey(String contactKey) {
        return customerProfileRepository.findRootCustomerCodeByContactKey(contactKey);
    }

    public Optional<ClueResponse> findClueByCustomerCodeForUpdate(String customerCode) {
        if (!StringUtils.hasText(customerCode)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT customer_code, source_platform, add_method, contact_info, has_wechat_id, uploader, uploader_employee_code,
                                   org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                                   assigned_sales_employee_code, deposit_amount, remaining_balance, status_remark, refund_amount, refunded_at,
                                   landing_at, landing_remark, created_at_text, updated_at_text
                            FROM crm_clues
                            WHERE customer_code = ?
                            FOR UPDATE
                            """,
                    (rs, rowNum) -> readClueRow(rs),
                    customerCode));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public long countCluesCreatedOn(LocalDate date) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM crm_clues WHERE created_at_value >= ? AND created_at_value < ?",
                Long.class,
                Timestamp.valueOf(date.atStartOfDay()),
                Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
    }

    @Transactional
    public int nextClueDailySequence(LocalDate date, String sequenceScope) {
        return sequenceRepository.nextClueDailySequence(date, sequenceScope);
    }

    @Transactional
    public int nextDealDailySequence(LocalDate date, String sequenceScope) {
        return sequenceRepository.nextDealDailySequence(date, sequenceScope);
    }

    public void acquireContactLock(String contactKey) {
        customerProfileRepository.acquireContactLock(contactKey);
    }

    public void releaseContactLock(String contactKey) {
        // Row locks are released automatically when the surrounding transaction ends.
    }

    public boolean clueExists(String customerCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_clues WHERE customer_code = ?", Long.class, customerCode);
        return count != null && count > 0;
    }

    public PageResponse<ClueResponse> queryClueListPage(
            List<String> visibleUploaderCodes,
            String visibleSalesCode,
            String keyword,
            String customerCode,
            String contactInfo,
            String sourcePlatform,
            String addMethod,
            String status,
            String uploader,
            String assignedSales,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildClueWhereSql(visibleUploaderCodes, visibleSalesCode, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, params);
        return queryCluePage(where, params, page, pageSize);
    }

    public List<ClueResponse> queryClueListForExport(
            List<String> visibleUploaderCodes,
            String visibleSalesCode,
            String keyword,
            String customerCode,
            String contactInfo,
            String sourcePlatform,
            String addMethod,
            String status,
            String uploader,
            String assignedSales,
            String startDate,
            String endDate,
            int limit
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildClueWhereSql(visibleUploaderCodes, visibleSalesCode, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, params);
        params.add(Math.max(1, Math.min(limit, 50000)));
        return jdbcTemplate.query("""
                        SELECT customer_code, source_platform, add_method, contact_info, has_wechat_id, uploader, uploader_employee_code,
                               org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                               assigned_sales_employee_code, deposit_amount, remaining_balance, status_remark, refund_amount, refunded_at,
                               landing_at, landing_remark, created_at_text, updated_at_text
                        FROM crm_clues
                        WHERE """ + where + """
                        ORDER BY COALESCE(updated_at_value, created_at_value) DESC, customer_code DESC
                        LIMIT ?
                        """,
                (rs, rowNum) -> readClueListRow(rs),
                params.toArray());
    }

    public PageResponse<ClueResponse> queryPublicSalesPoolPage(
            List<String> visibleUploaderCodes,
            boolean unrestricted,
            String keyword,
            String customerCode,
            String contactInfo,
            String sourcePlatform,
            String addMethod,
            String status,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildClueWhereSql(unrestricted ? null : visibleUploaderCodes, null, keyword, customerCode, contactInfo, sourcePlatform, addMethod, null, null, null, startDate, endDate, params);
        where += " AND (assigned_sales_employee_code IS NULL OR assigned_sales_employee_code = '')";
        where += " AND status NOT IN ('DEPOSIT_PAID', 'REFUNDED', 'LANDED', 'DELETED')";
        if (!StringUtils.hasText(status) || "PENDING".equalsIgnoreCase(status)) {
            where += " AND status IN ('NEW', 'FOLLOWING', 'TO_DEAL')";
        } else {
            where += " AND status = ?";
            params.add(status.trim().toUpperCase());
        }
        return queryCluePage(where, params, page, pageSize);
    }

    public PageResponse<ClueResponse> queryMySalesPoolPage(
            String salesEmployeeCode,
            String keyword,
            String customerCode,
            String contactInfo,
            String sourcePlatform,
            String addMethod,
            String status,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildClueWhereSql(null, null, keyword, customerCode, contactInfo, sourcePlatform, addMethod, null, null, null, startDate, endDate, params);
        if (StringUtils.hasText(salesEmployeeCode)) {
            where += " AND assigned_sales_employee_code = ?";
            params.add(salesEmployeeCode);
        }
        if (StringUtils.hasText(status)) {
            if ("PENDING".equalsIgnoreCase(status)) {
                where += " AND status IN ('NEW', 'FOLLOWING', 'TO_DEAL')";
            } else {
                where += " AND status = ?";
                params.add(status.trim().toUpperCase());
            }
        }
        return queryCluePage(where, params, page, pageSize);
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
        return queryCluePage(where, params, page, pageSize);
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
                queryGroupedCounts(where, params, "COALESCE(NULLIF(uploader, ''), uploader_employee_code, '\u672a\u5f52\u5c5e')", true),
                queryGroupedCounts(where + " AND assigned_sales_employee_code IS NOT NULL AND assigned_sales_employee_code <> ''",
                        params,
                        "COALESCE(NULLIF(assigned_sales, ''), assigned_sales_employee_code, '\u672a\u5f52\u5c5e')",
                        true)
        );
    }

    public PageResponse<ClueResponse> queryEmployeeCluesPage(
            String employeeCode,
            boolean sales,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("status <> 'DELETED'");
        if (sales) {
            where.append(" AND assigned_sales_employee_code = ?");
        } else {
            where.append(" AND uploader_employee_code = ?");
        }
        params.add(employeeCode);
        appendDateTimeRange(where, params, "created_at_value", startDate, endDate);
        return queryCluePage(where.toString(), params, page, pageSize);
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
        appendAndInClause(where, params, employeeColumn, employeeCodes);
        appendDateTimeRange(where, params, "created_at_value", startDate, endDate);
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

    private PageResponse<ClueResponse> queryCluePage(String where, List<Object> params, Integer page, Integer pageSize) {
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_clues WHERE " + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        String pageSql = """
                SELECT customer_code, source_platform, add_method, contact_info, has_wechat_id, uploader, uploader_employee_code,
                       org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                       assigned_sales_employee_code, deposit_amount, remaining_balance, status_remark, refund_amount, refunded_at,
                       landing_at, landing_remark, created_at_text, updated_at_text
                FROM crm_clues
                """ + "WHERE " + where + "\n" + """
                        ORDER BY COALESCE(updated_at_value, created_at_value) DESC, customer_code DESC
                LIMIT ?, ?
                """;
        List<ClueResponse> rows = jdbcTemplate.query(pageSql,
                (rs, rowNum) -> readClueListRow(rs),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    public PageResponse<AssignLogReportRow> queryAssignLogReportPage(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String customerCode,
            String action,
            String operator,
            String salesEmployeeCode,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildAssignLogWhereSql(visibleUploaderCodes, visibleSalesCodes, customerCode, action, operator, salesEmployeeCode, startDate, endDate, params);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crm_clue_assign_logs log
                JOIN crm_clues clue ON clue.customer_code = log.customer_code
                """ + "WHERE " + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        List<AssignLogReportRow> rows = jdbcTemplate.query("""
                        SELECT log.customer_code, clue.source_platform, clue.contact_info, clue.status,
                               clue.uploader, clue.uploader_employee_code, clue.assigned_sales,
                               clue.assigned_sales_employee_code, log.action, log.action_text, log.operator,
                               log.operator_code, log.from_sales, log.from_sales_employee_code,
                               log.to_sales, log.to_sales_employee_code, log.remark, log.created_at_text
                        FROM crm_clue_assign_logs log
                        JOIN crm_clues clue ON clue.customer_code = log.customer_code
                        """ + "WHERE " + where + """
                        ORDER BY log.created_at_text DESC, log.id DESC
                        LIMIT ?, ?
                        """,
                (rs, rowNum) -> new AssignLogReportRow(
                        rs.getString("customer_code"),
                        rs.getString("source_platform"),
                        rs.getString("contact_info"),
                        rs.getString("status"),
                        rs.getString("uploader"),
                        rs.getString("uploader_employee_code"),
                        rs.getString("assigned_sales"),
                        rs.getString("assigned_sales_employee_code"),
                        rs.getString("action"),
                        rs.getString("action_text"),
                        rs.getString("operator"),
                        rs.getString("operator_code"),
                        rs.getString("from_sales"),
                        rs.getString("from_sales_employee_code"),
                        rs.getString("to_sales"),
                        rs.getString("to_sales_employee_code"),
                        rs.getString("remark"),
                        rs.getString("created_at_text")
                ),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    public PageResponse<OperationLogReportRow> queryOperationLogReportPage(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String customerCode,
            String operator,
            String field,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildOperationLogWhereSql(visibleUploaderCodes, visibleSalesCodes, customerCode, operator, field, startDate, endDate, params);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crm_clue_operation_logs log
                JOIN crm_clues clue ON clue.customer_code = log.customer_code
                """ + "WHERE " + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        List<OperationLogReportRow> rows = jdbcTemplate.query("""
                        SELECT log.customer_code, clue.source_platform, clue.contact_info, clue.status,
                               clue.uploader, clue.uploader_employee_code, clue.assigned_sales,
                               clue.assigned_sales_employee_code, log.action, log.action_text, log.operator,
                               log.operator_code, log.field_name, log.field_text, log.old_value,
                               log.new_value, log.created_at_text
                        FROM crm_clue_operation_logs log
                        JOIN crm_clues clue ON clue.customer_code = log.customer_code
                        """ + "WHERE " + where + """
                        ORDER BY log.created_at_text DESC, log.id DESC
                        LIMIT ?, ?
                        """,
                (rs, rowNum) -> new OperationLogReportRow(
                        rs.getString("customer_code"),
                        rs.getString("source_platform"),
                        rs.getString("contact_info"),
                        rs.getString("status"),
                        rs.getString("uploader"),
                        rs.getString("uploader_employee_code"),
                        rs.getString("assigned_sales"),
                        rs.getString("assigned_sales_employee_code"),
                        rs.getString("action"),
                        rs.getString("action_text"),
                        rs.getString("operator"),
                        rs.getString("operator_code"),
                        rs.getString("field_name"),
                        rs.getString("field_text"),
                        rs.getString("old_value"),
                        rs.getString("new_value"),
                        rs.getString("created_at_text")
                ),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    private String buildClueWhereSql(
            List<String> visibleUploaderCodes,
            String visibleSalesCode,
            String keyword,
            String customerCode,
            String contactInfo,
            String sourcePlatform,
            String addMethod,
            String status,
            String uploader,
            String assignedSales,
            String startDate,
            String endDate,
            List<Object> params
    ) {
        StringBuilder where = new StringBuilder("status <> 'DELETED'");
        if (visibleUploaderCodes != null || StringUtils.hasText(visibleSalesCode)) {
            where.append(" AND (");
            boolean hasUploaderScope = visibleUploaderCodes != null && !visibleUploaderCodes.isEmpty();
            if (visibleUploaderCodes != null && visibleUploaderCodes.isEmpty()) {
                where.append("1 = 0");
            } else if (hasUploaderScope) {
                where.append("uploader_employee_code IN (");
                where.append("?,".repeat(visibleUploaderCodes.size()));
                where.setLength(where.length() - 1);
                where.append(")");
                params.addAll(visibleUploaderCodes);
            }
            if (StringUtils.hasText(visibleSalesCode)) {
                if (hasUploaderScope || visibleUploaderCodes != null && visibleUploaderCodes.isEmpty()) {
                    where.append(" OR ");
                }
                where.append("assigned_sales_employee_code = ?");
                params.add(visibleSalesCode);
            }
            where.append(")");
        }
        appendLike(where, params, "customer_code", customerCode);
        appendLike(where, params, "contact_info", contactInfo);
        if (StringUtils.hasText(sourcePlatform)) {
            where.append(" AND source_platform = ?");
            params.add(sourcePlatform.trim().toUpperCase());
        }
        if (StringUtils.hasText(addMethod)) {
            where.append(" AND add_method = ?");
            params.add(addMethod.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase());
        }
        if (StringUtils.hasText(uploader)) {
            where.append(" AND (LOWER(uploader) LIKE ? OR LOWER(uploader_employee_code) LIKE ?)");
            String value = likeValue(uploader);
            params.add(value);
            params.add(value);
        }
        if (StringUtils.hasText(assignedSales)) {
            where.append(" AND (LOWER(assigned_sales) LIKE ? OR LOWER(assigned_sales_employee_code) LIKE ?)");
            String value = likeValue(assignedSales);
            params.add(value);
            params.add(value);
        }
        if (StringUtils.hasText(keyword)) {
            where.append("""
                    AND (
                      LOWER(customer_code) LIKE ?
                      OR LOWER(source_platform) LIKE ?
                      OR LOWER(add_method) LIKE ?
                      OR LOWER(contact_info) LIKE ?
                      OR LOWER(remark) LIKE ?
                      OR LOWER(status) LIKE ?
                      OR LOWER(uploader) LIKE ?
                      OR LOWER(uploader_employee_code) LIKE ?
                      OR LOWER(assigned_sales) LIKE ?
                      OR LOWER(assigned_sales_employee_code) LIKE ?
                    )
                    """);
            String value = likeValue(keyword);
            for (int i = 0; i < 10; i++) {
                params.add(value);
            }
        }
        appendDateTimeRange(where, params, "created_at_value", startDate, endDate);
        return where.toString();
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
                appendInClause(where, params, "uploader_employee_code", visibleUploaderCodes);
                appended = true;
            }
            if (hasSalesScope) {
                if (appended) {
                    where.append(" OR ");
                }
                appendInClause(where, params, "assigned_sales_employee_code", visibleSalesCodes);
            }
            where.append(")");
        }
        appendDateTimeRange(where, params, "created_at_value", startDate, endDate);
        return where.toString();
    }

    private void appendInClause(StringBuilder where, List<Object> params, String column, List<String> values) {
        if (values == null) {
            return;
        }
        if (values.isEmpty()) {
            where.append("1 = 0");
            return;
        }
        where.append(column).append(" IN (");
        where.append("?,".repeat(values.size()));
        where.setLength(where.length() - 1);
        where.append(")");
        params.addAll(values);
    }

    private void appendAndInClause(StringBuilder where, List<Object> params, String column, List<String> values) {
        where.append(" AND ");
        appendInClause(where, params, column, values);
    }

    private String buildAssignLogWhereSql(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String customerCode,
            String action,
            String operator,
            String salesEmployeeCode,
            String startDate,
            String endDate,
            List<Object> params
    ) {
        StringBuilder where = new StringBuilder("clue.status <> 'DELETED'");
        boolean hasUploaderScope = visibleUploaderCodes != null;
        boolean hasSalesScope = visibleSalesCodes != null;
        if (hasUploaderScope || hasSalesScope) {
            where.append(" AND (");
            boolean appended = false;
            if (hasUploaderScope) {
                appendInClause(where, params, "clue.uploader_employee_code", visibleUploaderCodes);
                appended = true;
            }
            if (hasSalesScope) {
                if (appended) {
                    where.append(" OR ");
                }
                where.append("(");
                appendInClause(where, params, "clue.assigned_sales_employee_code", visibleSalesCodes);
                where.append(" OR ");
                appendInClause(where, params, "log.from_sales_employee_code", visibleSalesCodes);
                where.append(" OR ");
                appendInClause(where, params, "log.to_sales_employee_code", visibleSalesCodes);
                where.append(" OR ");
                appendInClause(where, params, "log.operator_code", visibleSalesCodes);
                where.append(")");
            }
            where.append(")");
        }
        appendLike(where, params, "log.customer_code", customerCode);
        if (StringUtils.hasText(action)) {
            where.append(" AND log.action = ?");
            params.add(action.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(operator)) {
            where.append(" AND (LOWER(log.operator) LIKE ? OR LOWER(log.operator_code) LIKE ?)");
            String value = likeValue(operator);
            params.add(value);
            params.add(value);
        }
        if (StringUtils.hasText(salesEmployeeCode)) {
            where.append("""
                    AND (
                      UPPER(log.from_sales_employee_code) = ?
                      OR UPPER(log.to_sales_employee_code) = ?
                      OR UPPER(clue.assigned_sales_employee_code) = ?
                    )
                    """);
            String normalizedSales = salesEmployeeCode.trim().toUpperCase(Locale.ROOT);
            params.add(normalizedSales);
            params.add(normalizedSales);
            params.add(normalizedSales);
        }
        appendTextDateRange(where, params, "log.created_at_text", startDate, endDate);
        return where.toString();
    }

    private String buildOperationLogWhereSql(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String customerCode,
            String operator,
            String field,
            String startDate,
            String endDate,
            List<Object> params
    ) {
        StringBuilder where = new StringBuilder("clue.status <> 'DELETED'");
        boolean hasUploaderScope = visibleUploaderCodes != null;
        boolean hasSalesScope = visibleSalesCodes != null;
        if (hasUploaderScope || hasSalesScope) {
            where.append(" AND (");
            boolean appended = false;
            if (hasUploaderScope) {
                appendInClause(where, params, "clue.uploader_employee_code", visibleUploaderCodes);
                appended = true;
            }
            if (hasSalesScope) {
                if (appended) {
                    where.append(" OR ");
                }
                where.append("(");
                appendInClause(where, params, "clue.assigned_sales_employee_code", visibleSalesCodes);
                where.append(" OR ");
                appendInClause(where, params, "log.operator_code", visibleSalesCodes);
                where.append(")");
            }
            where.append(")");
        }
        appendLike(where, params, "log.customer_code", customerCode);
        if (StringUtils.hasText(operator)) {
            where.append(" AND (LOWER(log.operator) LIKE ? OR LOWER(log.operator_code) LIKE ?)");
            String value = likeValue(operator);
            params.add(value);
            params.add(value);
        }
        if (StringUtils.hasText(field)) {
            where.append(" AND (LOWER(log.field_name) LIKE ? OR LOWER(log.field_text) LIKE ?)");
            String value = likeValue(field);
            params.add(value);
            params.add(value);
        }
        appendTextDateRange(where, params, "log.created_at_text", startDate, endDate);
        return where.toString();
    }

    private void appendTextDateRange(StringBuilder where, List<Object> params, String column, String startDate, String endDate) {
        if (StringUtils.hasText(startDate)) {
            where.append(" AND ").append(column).append(" >= ?");
            params.add(startDate.trim());
        }
        if (StringUtils.hasText(endDate)) {
            where.append(" AND ").append(column).append(" <= ?");
            params.add(endDate.trim() + " 23:59");
        }
    }

    private void appendDateTimeRange(StringBuilder where, List<Object> params, String column, String startDate, String endDate) {
        if (StringUtils.hasText(startDate)) {
            Timestamp start = parseDateBoundary(startDate, false);
            if (start != null) {
                where.append(" AND ").append(column).append(" >= ?");
                params.add(start);
            }
        }
        if (StringUtils.hasText(endDate)) {
            Timestamp end = parseDateBoundary(endDate, true);
            if (end != null) {
                where.append(" AND ").append(column).append(" <= ?");
                params.add(end);
            }
        }
    }

    private void appendDateRange(StringBuilder where, List<Object> params, String column, String startDate, String endDate) {
        java.sql.Date start = parseDate(startDate);
        if (start != null) {
            where.append(" AND ").append(column).append(" >= ?");
            params.add(start);
        }
        java.sql.Date end = parseDate(endDate);
        if (end != null) {
            where.append(" AND ").append(column).append(" <= ?");
            params.add(end);
        }
    }

    private void appendLike(StringBuilder where, List<Object> params, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        where.append(" AND LOWER(").append(column).append(") LIKE ?");
        params.add(likeValue(value));
    }

    private String likeValue(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private String contactKey(String contactInfo) {
        if (!StringUtils.hasText(contactInfo)) {
            return null;
        }
        String normalized = contactInfo.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    @Transactional
    public void writeClues(List<ClueResponse> rows) {
        for (ClueResponse row : rows) {
            writeClue(row);
        }
    }

    @Transactional
    public void writeClue(ClueResponse row) {
        upsertClue(row);
        replaceClueChildren(row);
    }

    @Transactional
    public boolean insertClue(ClueResponse row) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO crm_clues (
                              customer_code, source_platform, add_method, contact_info, contact_key, has_wechat_id, uploader, uploader_employee_code,
                              org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                              assigned_sales_employee_code, deposit_amount, deposit_amount_value, remaining_balance, remaining_balance_value, status_remark,
                              refund_amount, refund_amount_value, refunded_at, refunded_at_value, landing_at,
                              landing_at_value, landing_remark, created_at_text, created_at_value, updated_at_text,
                              updated_at_value
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
                    clueParams(row));
            replaceClueChildren(row);
            customerProfileRepository.upsertCustomerProfile(row);
            return true;
        } catch (DuplicateKeyException error) {
            return false;
        }
    }

    private void upsertClue(ClueResponse row) {
        jdbcTemplate.update("""
                            INSERT INTO crm_clues (
                              customer_code, source_platform, add_method, contact_info, contact_key, has_wechat_id, uploader, uploader_employee_code,
                              org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                              assigned_sales_employee_code, deposit_amount, deposit_amount_value, remaining_balance, remaining_balance_value, status_remark,
                              refund_amount, refund_amount_value, refunded_at, refunded_at_value, landing_at,
                              landing_at_value, landing_remark, created_at_text, created_at_value, updated_at_text,
                              updated_at_value
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              source_platform = VALUES(source_platform),
                              add_method = VALUES(add_method),
                              contact_info = VALUES(contact_info),
                              contact_key = VALUES(contact_key),
                              has_wechat_id = VALUES(has_wechat_id),
                              uploader = VALUES(uploader),
                              uploader_employee_code = VALUES(uploader_employee_code),
                              org_type = VALUES(org_type),
                              branch_id = VALUES(branch_id),
                              branch_name = VALUES(branch_name),
                              status = VALUES(status),
                              remark = VALUES(remark),
                              repeat_demand = VALUES(repeat_demand),
                              original_customer_code = VALUES(original_customer_code),
                              demand_sequence = VALUES(demand_sequence),
                              assigned_sales = VALUES(assigned_sales),
                              assigned_sales_employee_code = VALUES(assigned_sales_employee_code),
                              deposit_amount = VALUES(deposit_amount),
                              deposit_amount_value = VALUES(deposit_amount_value),
                              remaining_balance = VALUES(remaining_balance),
                              remaining_balance_value = VALUES(remaining_balance_value),
                              status_remark = VALUES(status_remark),
                              refund_amount = VALUES(refund_amount),
                              refund_amount_value = VALUES(refund_amount_value),
                              refunded_at = VALUES(refunded_at),
                              refunded_at_value = VALUES(refunded_at_value),
                              landing_at = VALUES(landing_at),
                              landing_at_value = VALUES(landing_at_value),
                              landing_remark = VALUES(landing_remark),
                              created_at_text = VALUES(created_at_text),
                              created_at_value = VALUES(created_at_value),
                              updated_at_text = VALUES(updated_at_text),
                              updated_at_value = VALUES(updated_at_value)
                            """,
                clueParams(row));
        customerProfileRepository.upsertCustomerProfile(row);
    }

    private Object[] clueParams(ClueResponse row) {
        return new Object[]{
                row.customerCode(), row.sourcePlatform(), row.addMethod(), row.contactInfo(), contactKey(row.contactInfo()), bool(row.hasWechatId()), row.uploader(),
                row.uploaderEmployeeCode(), row.orgType(), row.branchId(), row.branchName(), row.status(), row.remark(), bool(row.repeatDemand()), row.originalCustomerCode(),
                row.demandSequence(), row.assignedSales(), row.assignedSalesEmployeeCode(), row.depositAmount(),
                parseMoney(row.depositAmount()), row.remainingBalance(), parseMoney(row.remainingBalance()), row.statusRemark(), row.refundAmount(), parseMoney(row.refundAmount()),
                row.refundedAt(), parseDateTime(row.refundedAt()), row.landingAt(), parseDateTime(row.landingAt()),
                row.landingRemark(), row.createdAt(), parseDateTime(row.createdAt()), row.updatedAt(), parseDateTime(row.updatedAt())
        };
    }

    public void deleteClue(String customerCode) {
        deleteClueChildren(customerCode);
        jdbcTemplate.update("DELETE FROM crm_clues WHERE customer_code = ?", customerCode);
    }

    public Optional<DealResponse> findDealByCode(String dealCode) {
        if (!StringUtils.hasText(dealCode)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date, quote_text,
                                   travel_date, itinerary, deal_date, deal_user, deal_user_code, total_deal_sequence,
                                   personal_deal_sequence, status, refund_amount, refund_remark, refunded_at, landing_at,
                                   landing_remark, created_at_text, updated_at_text
                            FROM crm_deals
                            WHERE deal_code = ?
                            """,
                    (rs, rowNum) -> readDealRow(rs),
                    dealCode));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public boolean dealExistsForCustomer(String customerCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_deals WHERE customer_code = ?", Long.class, customerCode);
        return count != null && count > 0;
    }

    public long countDeals() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_deals", Long.class);
        return count == null ? 0 : count;
    }

    public long countDealsByUser(String employeeCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_deals WHERE deal_user_code = ?", Long.class, employeeCode);
        return count == null ? 0 : count;
    }

    public boolean dealExists(String dealCode) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_deals WHERE deal_code = ?", Long.class, dealCode);
        return count != null && count > 0;
    }

    public PageResponse<DealResponse> queryDealReportPage(
            String keyword,
            String dealCode,
            String customerCode,
            String customerName,
            String status,
            String startDate,
            String endDate,
            String salesEmployeeCode,
            Integer page,
            Integer pageSize
    ) {
        List<Object> unionParams = new ArrayList<>();
        String salesWhere = "";
        if (StringUtils.hasText(salesEmployeeCode)) {
            salesWhere = " AND UPPER(deal_user_code) = ?";
            unionParams.add(salesEmployeeCode.trim().toUpperCase(Locale.ROOT));
        }
        String unionSql = """
                SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date,
                       quote_text, travel_date, itinerary, deal_date, deal_user, deal_user_code,
                       total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_remark,
                       refunded_at, landing_at, landing_remark, created_at_text, updated_at_text,
                       deal_date_value, created_at_value, updated_at_value
                FROM (
                  SELECT d.deal_code,
                         d.customer_code,
                         d.customer_name,
                         COALESCE(NULLIF(c.deposit_amount, ''), d.deposit) AS deposit,
                         COALESCE(NULLIF(c.remaining_balance, ''), d.remaining_balance) AS remaining_balance,
                         d.booking_date,
                         d.add_wechat_date,
                         d.quote_text,
                         d.travel_date,
                         d.itinerary,
                         d.deal_date,
                         d.deal_user,
                         d.deal_user_code,
                         d.total_deal_sequence,
                         d.personal_deal_sequence,
                         CASE WHEN c.status IN ('DEPOSIT_PAID', 'REFUNDED', 'LANDED') THEN c.status ELSE d.status END AS status,
                         CASE WHEN c.status = 'REFUNDED' AND c.refund_amount IS NOT NULL AND c.refund_amount <> '' THEN c.refund_amount ELSE d.refund_amount END AS refund_amount,
                         CASE WHEN c.status = 'REFUNDED' AND c.status_remark IS NOT NULL AND c.status_remark <> '' THEN c.status_remark ELSE d.refund_remark END AS refund_remark,
                         CASE WHEN c.status = 'REFUNDED' AND c.refunded_at IS NOT NULL AND c.refunded_at <> '' THEN c.refunded_at ELSE d.refunded_at END AS refunded_at,
                         CASE WHEN c.status = 'LANDED' AND c.landing_at IS NOT NULL AND c.landing_at <> '' THEN c.landing_at ELSE d.landing_at END AS landing_at,
                         CASE WHEN c.status = 'LANDED' AND c.landing_remark IS NOT NULL AND c.landing_remark <> '' THEN c.landing_remark ELSE d.landing_remark END AS landing_remark,
                         d.created_at_text,
                         d.updated_at_text,
                         d.deal_date_value,
                         d.created_at_value,
                         d.updated_at_value
                  FROM crm_deals d
                  LEFT JOIN crm_clues c ON c.customer_code = d.customer_code
                  WHERE 1 = 1""" + salesWhere + """
                  UNION ALL
                  SELECT CONCAT('AUTO-', c.customer_code) AS deal_code,
                         c.customer_code,
                         CASE WHEN c.contact_info IS NOT NULL AND c.contact_info <> '' THEN c.contact_info ELSE c.customer_code END AS customer_name,
                         c.deposit_amount AS deposit,
                         c.remaining_balance AS remaining_balance,
                         '' AS booking_date,
                         '' AS add_wechat_date,
                         '' AS quote_text,
                         '' AS travel_date,
                         c.remark AS itinerary,
                         SUBSTRING(COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text), 1, 10) AS deal_date,
                         c.assigned_sales AS deal_user,
                         c.assigned_sales_employee_code AS deal_user_code,
                         0 AS total_deal_sequence,
                         0 AS personal_deal_sequence,
                         c.status,
                         c.refund_amount,
                         c.status_remark AS refund_remark,
                         c.refunded_at,
                         c.landing_at,
                         c.landing_remark,
                         COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text) AS created_at_text,
                         COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text) AS updated_at_text,
                         DATE(COALESCE(c.updated_at_value, c.created_at_value)) AS deal_date_value,
                         COALESCE(c.updated_at_value, c.created_at_value) AS created_at_value,
                         COALESCE(c.updated_at_value, c.created_at_value) AS updated_at_value
                  FROM crm_clues c
                  WHERE c.status IN ('DEPOSIT_PAID', 'REFUNDED', 'LANDED')
                    AND c.status <> 'DELETED'
                    AND c.assigned_sales_employee_code IS NOT NULL
                    AND c.assigned_sales_employee_code <> ''
                    AND NOT EXISTS (SELECT 1 FROM crm_deals d WHERE d.customer_code = c.customer_code)
                """;
        if (StringUtils.hasText(salesEmployeeCode)) {
            unionSql += " AND UPPER(c.assigned_sales_employee_code) = ?";
            unionParams.add(salesEmployeeCode.trim().toUpperCase(Locale.ROOT));
        }
        unionSql += "\n) deal_rows";

        List<Object> filterParams = new ArrayList<>();
        String where = buildDealReportWhereSql(keyword, dealCode, customerCode, customerName, status, startDate, endDate, filterParams);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        List<Object> countParams = new ArrayList<>(unionParams);
        countParams.addAll(filterParams);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM (" + unionSql + ") deal_view WHERE " + where, Long.class, countParams.toArray());

        List<Object> pageParams = new ArrayList<>(countParams);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        String pageSql = """
                SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date,
                       quote_text, travel_date, itinerary, deal_date, deal_user, deal_user_code,
                       total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_remark,
                       refunded_at, landing_at, landing_remark, created_at_text, updated_at_text
                FROM (
                """ + unionSql
                + "\n) deal_view\n"
                + "WHERE " + where + "\n"
                + "ORDER BY COALESCE(updated_at_value, created_at_value) DESC, deal_code DESC\n"
                + "LIMIT ?, ?";
        List<DealResponse> rows = jdbcTemplate.query(pageSql,
                (rs, rowNum) -> readDealRow(rs),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    public List<DealResponse> queryDealReportForExport(
            String keyword,
            String dealCode,
            String customerCode,
            String customerName,
            String status,
            String startDate,
            String endDate,
            String salesEmployeeCode,
            int limit
    ) {
        DealReportSql reportSql = buildDealReportSql(salesEmployeeCode);
        List<Object> filterParams = new ArrayList<>();
        String where = buildDealReportWhereSql(keyword, dealCode, customerCode, customerName, status, startDate, endDate, filterParams);
        List<Object> params = new ArrayList<>(reportSql.params());
        params.addAll(filterParams);
        params.add(Math.max(1, Math.min(limit, 50000)));
        String sql = """
                SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date,
                       quote_text, travel_date, itinerary, deal_date, deal_user, deal_user_code,
                       total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_remark,
                       refunded_at, landing_at, landing_remark, created_at_text, updated_at_text
                FROM (
                """ + reportSql.sql()
                + "\n) deal_view\n"
                + "WHERE " + where + "\n"
                + "ORDER BY COALESCE(updated_at_value, created_at_value) DESC, deal_code DESC\n"
                + "LIMIT ?";
        return jdbcTemplate.query(sql, (rs, rowNum) -> readDealRow(rs), params.toArray());
    }

    private String buildDealReportWhereSql(
            String keyword,
            String dealCode,
            String customerCode,
            String customerName,
            String status,
            String startDate,
            String endDate,
            List<Object> params
    ) {
        StringBuilder where = new StringBuilder("1 = 1");
        appendLike(where, params, "deal_code", dealCode);
        appendLike(where, params, "customer_code", customerCode);
        appendLike(where, params, "customer_name", customerName);
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            where.append("""
                    AND (
                      LOWER(deal_code) LIKE ?
                      OR LOWER(customer_code) LIKE ?
                      OR LOWER(customer_name) LIKE ?
                      OR LOWER(deal_user) LIKE ?
                      OR LOWER(deal_user_code) LIKE ?
                      OR LOWER(status) LIKE ?
                      OR LOWER(refund_remark) LIKE ?
                      OR LOWER(quote_text) LIKE ?
                      OR LOWER(itinerary) LIKE ?
                    )
                    """);
            String value = likeValue(keyword);
            for (int i = 0; i < 9; i++) {
                params.add(value);
            }
        }
        appendDateRange(where, params, "deal_date_value", startDate, endDate);
        return where.toString();
    }

    private DealReportSql buildDealReportSql(String salesEmployeeCode) {
        List<Object> unionParams = new ArrayList<>();
        String salesWhere = "";
        if (StringUtils.hasText(salesEmployeeCode)) {
            salesWhere = " AND UPPER(deal_user_code) = ?";
            unionParams.add(salesEmployeeCode.trim().toUpperCase(Locale.ROOT));
        }
        String unionSql = """
                SELECT deal_code, customer_code, customer_name, deposit, remaining_balance, booking_date, add_wechat_date,
                       quote_text, travel_date, itinerary, deal_date, deal_user, deal_user_code,
                       total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_remark,
                       refunded_at, landing_at, landing_remark, created_at_text, updated_at_text,
                       deal_date_value, created_at_value, updated_at_value
                FROM (
                  SELECT d.deal_code,
                         d.customer_code,
                         d.customer_name,
                         COALESCE(NULLIF(c.deposit_amount, ''), d.deposit) AS deposit,
                         COALESCE(NULLIF(c.remaining_balance, ''), d.remaining_balance) AS remaining_balance,
                         d.booking_date,
                         d.add_wechat_date,
                         d.quote_text,
                         d.travel_date,
                         d.itinerary,
                         d.deal_date,
                         d.deal_user,
                         d.deal_user_code,
                         d.total_deal_sequence,
                         d.personal_deal_sequence,
                         CASE WHEN c.status IN ('DEPOSIT_PAID', 'REFUNDED', 'LANDED') THEN c.status ELSE d.status END AS status,
                         CASE WHEN c.status = 'REFUNDED' AND c.refund_amount IS NOT NULL AND c.refund_amount <> '' THEN c.refund_amount ELSE d.refund_amount END AS refund_amount,
                         CASE WHEN c.status = 'REFUNDED' AND c.status_remark IS NOT NULL AND c.status_remark <> '' THEN c.status_remark ELSE d.refund_remark END AS refund_remark,
                         CASE WHEN c.status = 'REFUNDED' AND c.refunded_at IS NOT NULL AND c.refunded_at <> '' THEN c.refunded_at ELSE d.refunded_at END AS refunded_at,
                         CASE WHEN c.status = 'LANDED' AND c.landing_at IS NOT NULL AND c.landing_at <> '' THEN c.landing_at ELSE d.landing_at END AS landing_at,
                         CASE WHEN c.status = 'LANDED' AND c.landing_remark IS NOT NULL AND c.landing_remark <> '' THEN c.landing_remark ELSE d.landing_remark END AS landing_remark,
                         d.created_at_text,
                         d.updated_at_text,
                         d.deal_date_value,
                         d.created_at_value,
                         d.updated_at_value
                  FROM crm_deals d
                  LEFT JOIN crm_clues c ON c.customer_code = d.customer_code
                  WHERE 1 = 1""" + salesWhere + """
                  UNION ALL
                  SELECT CONCAT('AUTO-', c.customer_code) AS deal_code,
                         c.customer_code,
                         CASE WHEN c.contact_info IS NOT NULL AND c.contact_info <> '' THEN c.contact_info ELSE c.customer_code END AS customer_name,
                         c.deposit_amount AS deposit,
                         c.remaining_balance AS remaining_balance,
                         '' AS booking_date,
                         '' AS add_wechat_date,
                         '' AS quote_text,
                         '' AS travel_date,
                         c.remark AS itinerary,
                         SUBSTRING(COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text), 1, 10) AS deal_date,
                         c.assigned_sales AS deal_user,
                         c.assigned_sales_employee_code AS deal_user_code,
                         0 AS total_deal_sequence,
                         0 AS personal_deal_sequence,
                         c.status,
                         c.refund_amount,
                         c.status_remark AS refund_remark,
                         c.refunded_at,
                         c.landing_at,
                         c.landing_remark,
                         COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text) AS created_at_text,
                         COALESCE(NULLIF(c.updated_at_text, ''), c.created_at_text) AS updated_at_text,
                         DATE(COALESCE(c.updated_at_value, c.created_at_value)) AS deal_date_value,
                         COALESCE(c.updated_at_value, c.created_at_value) AS created_at_value,
                         COALESCE(c.updated_at_value, c.created_at_value) AS updated_at_value
                  FROM crm_clues c
                  WHERE c.status IN ('DEPOSIT_PAID', 'REFUNDED', 'LANDED')
                    AND c.status <> 'DELETED'
                    AND c.assigned_sales_employee_code IS NOT NULL
                    AND c.assigned_sales_employee_code <> ''
                    AND NOT EXISTS (SELECT 1 FROM crm_deals d WHERE d.customer_code = c.customer_code)
                """;
        if (StringUtils.hasText(salesEmployeeCode)) {
            unionSql += " AND UPPER(c.assigned_sales_employee_code) = ?";
            unionParams.add(salesEmployeeCode.trim().toUpperCase(Locale.ROOT));
        }
        unionSql += "\n) deal_rows";
        return new DealReportSql(unionSql, unionParams);
    }

    private record DealReportSql(String sql, List<Object> params) {
    }

    private DealResponse readDealRow(ResultSet rs) throws SQLException {
        return new DealResponse(
                rs.getString("deal_code"),
                rs.getString("customer_code"),
                rs.getString("customer_name"),
                rs.getString("deposit"),
                rs.getString("remaining_balance"),
                rs.getString("booking_date"),
                rs.getString("add_wechat_date"),
                rs.getString("quote_text"),
                rs.getString("travel_date"),
                rs.getString("itinerary"),
                rs.getString("deal_date"),
                rs.getString("deal_user"),
                rs.getString("deal_user_code"),
                intOrNull(rs, "total_deal_sequence"),
                intOrNull(rs, "personal_deal_sequence"),
                rs.getString("status"),
                rs.getString("refund_amount"),
                rs.getString("refund_remark"),
                rs.getString("refunded_at"),
                rs.getString("landing_at"),
                rs.getString("landing_remark"),
                rs.getString("created_at_text"),
                rs.getString("updated_at_text")
        );
    }

    @Transactional
    public void writeDeals(List<DealResponse> rows) {
        for (DealResponse row : rows) {
            writeDeal(row);
        }
    }

    @Transactional
    public void writeDeal(DealResponse row) {
        upsertDeal(row);
    }

    @Transactional
    public boolean insertDeal(DealResponse row) {
        try {
            jdbcTemplate.update("""
                            INSERT INTO crm_deals (
                              deal_code, customer_code, customer_name, deposit, deposit_value, remaining_balance, remaining_balance_value, booking_date,
                              booking_date_value, add_wechat_date, add_wechat_date_value, quote_text, travel_date,
                              travel_date_value, itinerary, deal_date, deal_date_value, deal_user, deal_user_code,
                              total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_amount_value,
                              refund_remark, refunded_at, refunded_at_value, landing_at, landing_at_value,
                              landing_remark, created_at_text, created_at_value, updated_at_text, updated_at_value
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    dealParams(row));
            return true;
        } catch (DuplicateKeyException error) {
            return false;
        }
    }

    private void upsertDeal(DealResponse row) {
        jdbcTemplate.update("""
                            INSERT INTO crm_deals (
                              deal_code, customer_code, customer_name, deposit, deposit_value, remaining_balance, remaining_balance_value, booking_date,
                              booking_date_value, add_wechat_date, add_wechat_date_value, quote_text, travel_date,
                              travel_date_value, itinerary, deal_date, deal_date_value, deal_user, deal_user_code,
                              total_deal_sequence, personal_deal_sequence, status, refund_amount, refund_amount_value,
                              refund_remark, refunded_at, refunded_at_value, landing_at, landing_at_value,
                              landing_remark, created_at_text, created_at_value, updated_at_text, updated_at_value
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              customer_code = VALUES(customer_code),
                              customer_name = VALUES(customer_name),
                              deposit = VALUES(deposit),
                              deposit_value = VALUES(deposit_value),
                              remaining_balance = VALUES(remaining_balance),
                              remaining_balance_value = VALUES(remaining_balance_value),
                              booking_date = VALUES(booking_date),
                              booking_date_value = VALUES(booking_date_value),
                              add_wechat_date = VALUES(add_wechat_date),
                              add_wechat_date_value = VALUES(add_wechat_date_value),
                              quote_text = VALUES(quote_text),
                              travel_date = VALUES(travel_date),
                              travel_date_value = VALUES(travel_date_value),
                              itinerary = VALUES(itinerary),
                              deal_date = VALUES(deal_date),
                              deal_date_value = VALUES(deal_date_value),
                              deal_user = VALUES(deal_user),
                              deal_user_code = VALUES(deal_user_code),
                              total_deal_sequence = VALUES(total_deal_sequence),
                              personal_deal_sequence = VALUES(personal_deal_sequence),
                              status = VALUES(status),
                              refund_amount = VALUES(refund_amount),
                              refund_amount_value = VALUES(refund_amount_value),
                              refund_remark = VALUES(refund_remark),
                              refunded_at = VALUES(refunded_at),
                              refunded_at_value = VALUES(refunded_at_value),
                              landing_at = VALUES(landing_at),
                              landing_at_value = VALUES(landing_at_value),
                              landing_remark = VALUES(landing_remark),
                              created_at_text = VALUES(created_at_text),
                              created_at_value = VALUES(created_at_value),
                              updated_at_text = VALUES(updated_at_text),
                              updated_at_value = VALUES(updated_at_value)
                            """,
                dealParams(row));
    }

    private Object[] dealParams(DealResponse row) {
        return new Object[]{
                row.dealCode(), row.customerCode(), row.customerName(), row.deposit(), parseMoney(row.deposit()),
                row.remainingBalance(), parseMoney(row.remainingBalance()), row.bookingDate(), parseDate(row.bookingDate()), row.addWechatDate(), parseDate(row.addWechatDate()),
                row.quoteText(), row.travelDate(), parseDate(row.travelDate()), row.itinerary(), row.dealDate(),
                parseDate(row.dealDate()), row.dealUser(), row.dealUserCode(), row.totalDealSequence(),
                row.personalDealSequence(), row.status(), row.refundAmount(), parseMoney(row.refundAmount()),
                row.refundRemark(), row.refundedAt(), parseDateTime(row.refundedAt()), row.landingAt(),
                parseDateTime(row.landingAt()), row.landingRemark(), row.createdAt(), parseDateTime(row.createdAt()),
                row.updatedAt(), parseDateTime(row.updatedAt())
        };
    }

    public void deleteDeal(String dealCode) {
        jdbcTemplate.update("DELETE FROM crm_deals WHERE deal_code = ?", dealCode);
    }

    private ClueResponse readClueRow(ResultSet rs) throws SQLException {
        String customerCode = rs.getString("customer_code");
        return new ClueResponse(
                customerCode,
                rs.getString("source_platform"),
                rs.getString("add_method"),
                rs.getString("contact_info"),
                rs.getInt("has_wechat_id") == 1,
                rs.getString("uploader"),
                rs.getString("uploader_employee_code"),
                rs.getString("org_type"),
                rs.getString("branch_id"),
                rs.getString("branch_name"),
                rs.getString("status"),
                rs.getString("remark"),
                clueImageRepository.readImages(customerCode, "DOUYIN"),
                clueImageRepository.readImages(customerCode, "WECHAT"),
                rs.getInt("repeat_demand") == 1,
                rs.getString("original_customer_code"),
                intOrNull(rs, "demand_sequence"),
                rs.getString("assigned_sales"),
                rs.getString("assigned_sales_employee_code"),
                rs.getString("deposit_amount"),
                rs.getString("remaining_balance"),
                rs.getString("status_remark"),
                rs.getString("refund_amount"),
                rs.getString("refunded_at"),
                rs.getString("landing_at"),
                rs.getString("landing_remark"),
                readStatusHistory(customerCode),
                readFollowRecords(customerCode),
                readAssignLogs(customerCode),
                readOperationLogs(customerCode),
                rs.getString("created_at_text"),
                rs.getString("updated_at_text")
        );
    }

    private ClueResponse readClueListRow(ResultSet rs) throws SQLException {
        return new ClueResponse(
                rs.getString("customer_code"),
                rs.getString("source_platform"),
                rs.getString("add_method"),
                rs.getString("contact_info"),
                rs.getInt("has_wechat_id") == 1,
                rs.getString("uploader"),
                rs.getString("uploader_employee_code"),
                rs.getString("org_type"),
                rs.getString("branch_id"),
                rs.getString("branch_name"),
                rs.getString("status"),
                rs.getString("remark"),
                List.of(),
                List.of(),
                rs.getInt("repeat_demand") == 1,
                rs.getString("original_customer_code"),
                intOrNull(rs, "demand_sequence"),
                rs.getString("assigned_sales"),
                rs.getString("assigned_sales_employee_code"),
                rs.getString("deposit_amount"),
                rs.getString("remaining_balance"),
                rs.getString("status_remark"),
                rs.getString("refund_amount"),
                rs.getString("refunded_at"),
                rs.getString("landing_at"),
                rs.getString("landing_remark"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                rs.getString("created_at_text"),
                rs.getString("updated_at_text")
        );
    }

    private void replaceClueChildren(ClueResponse row) {
        deleteClueChildRowsOnly(row.customerCode());
        clueImageRepository.replaceImages(row.customerCode(), row.douyinImages(), row.wechatImages());
        writeStatusHistory(row.customerCode(), row.statusHistory());
        writeFollowRecords(row.customerCode(), row.followRecords());
        writeAssignLogs(row.customerCode(), row.assignLogs());
        writeOperationLogs(row.customerCode(), row.operationLogs());
    }

    private void deleteClueChildren(String customerCode) {
        deleteClueChildRowsOnly(customerCode);
        clueImageRepository.deleteImages(customerCode);
    }

    private void deleteClueChildRowsOnly(String customerCode) {
        jdbcTemplate.update("DELETE FROM crm_clue_status_history WHERE customer_code = ?", customerCode);
        jdbcTemplate.update("DELETE FROM crm_clue_follow_records WHERE customer_code = ?", customerCode);
        jdbcTemplate.update("DELETE FROM crm_clue_assign_logs WHERE customer_code = ?", customerCode);
        jdbcTemplate.update("DELETE FROM crm_clue_operation_logs WHERE customer_code = ?", customerCode);
    }

    private void writeStatusHistory(String customerCode, List<StatusChangeRecord> rows) {
        if (rows == null) {
            return;
        }
        for (StatusChangeRecord row : rows) {
            jdbcTemplate.update("""
                            INSERT INTO crm_clue_status_history (customer_code, status, status_text, operator, operator_code, deposit_amount, remark, created_at_text)
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    customerCode, row.status(), row.statusText(), row.operator(), row.operatorCode(), row.depositAmount(), row.remark(), row.createdAt());
        }
    }

    private List<StatusChangeRecord> readStatusHistory(String customerCode) {
        return jdbcTemplate.query("""
                        SELECT status, status_text, operator, operator_code, deposit_amount, remark, created_at_text
                        FROM crm_clue_status_history
                        WHERE customer_code = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> new StatusChangeRecord(
                        rs.getString("status"),
                        rs.getString("status_text"),
                        rs.getString("operator"),
                        rs.getString("operator_code"),
                        rs.getString("deposit_amount"),
                        rs.getString("remark"),
                        rs.getString("created_at_text")),
                customerCode);
    }

    private void writeFollowRecords(String customerCode, List<FollowRecord> rows) {
        if (rows == null) {
            return;
        }
        for (FollowRecord row : rows) {
            jdbcTemplate.update("""
                            INSERT INTO crm_clue_follow_records (customer_code, operator, operator_code, remark, created_at_text)
                            VALUES (?, ?, ?, ?, ?)
                            """,
                    customerCode, row.operator(), row.operatorCode(), row.remark(), row.createdAt());
        }
    }

    private List<FollowRecord> readFollowRecords(String customerCode) {
        return jdbcTemplate.query("""
                        SELECT operator, operator_code, remark, created_at_text
                        FROM crm_clue_follow_records
                        WHERE customer_code = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> new FollowRecord(rs.getString("operator"), rs.getString("operator_code"), rs.getString("remark"), rs.getString("created_at_text")),
                customerCode);
    }

    private void writeAssignLogs(String customerCode, List<AssignLogRecord> rows) {
        if (rows == null) {
            return;
        }
        for (AssignLogRecord row : rows) {
            jdbcTemplate.update("""
                            INSERT INTO crm_clue_assign_logs (
                              customer_code, action, action_text, operator, operator_code, from_sales,
                              from_sales_employee_code, to_sales, to_sales_employee_code, remark, created_at_text
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    customerCode, row.action(), row.actionText(), row.operator(), row.operatorCode(), row.fromSales(),
                    row.fromSalesEmployeeCode(), row.toSales(), row.toSalesEmployeeCode(), row.remark(), row.createdAt());
        }
    }

    private List<AssignLogRecord> readAssignLogs(String customerCode) {
        return jdbcTemplate.query("""
                        SELECT action, action_text, operator, operator_code, from_sales, from_sales_employee_code,
                               to_sales, to_sales_employee_code, remark, created_at_text
                        FROM crm_clue_assign_logs
                        WHERE customer_code = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> new AssignLogRecord(
                        rs.getString("action"),
                        rs.getString("action_text"),
                        rs.getString("operator"),
                        rs.getString("operator_code"),
                        rs.getString("from_sales"),
                        rs.getString("from_sales_employee_code"),
                        rs.getString("to_sales"),
                        rs.getString("to_sales_employee_code"),
                        rs.getString("remark"),
                        rs.getString("created_at_text")),
                customerCode);
    }

    private void writeOperationLogs(String customerCode, List<OperationLogRecord> rows) {
        if (rows == null) {
            return;
        }
        for (OperationLogRecord row : rows) {
            jdbcTemplate.update("""
                            INSERT INTO crm_clue_operation_logs (
                              customer_code, action, action_text, operator, operator_code, field_name,
                              field_text, old_value, new_value, created_at_text
                            )
                            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """,
                    customerCode, row.action(), row.actionText(), row.operator(), row.operatorCode(), row.field(),
                    row.fieldText(), row.oldValue(), row.newValue(), row.createdAt());
        }
    }

    private List<OperationLogRecord> readOperationLogs(String customerCode) {
        return jdbcTemplate.query("""
                        SELECT action, action_text, operator, operator_code, field_name, field_text, old_value, new_value, created_at_text
                        FROM crm_clue_operation_logs
                        WHERE customer_code = ?
                        ORDER BY id
                        """,
                (rs, rowNum) -> new OperationLogRecord(
                        rs.getString("action"),
                        rs.getString("action_text"),
                        rs.getString("operator"),
                        rs.getString("operator_code"),
                        rs.getString("field_name"),
                        rs.getString("field_text"),
                        rs.getString("old_value"),
                        rs.getString("new_value"),
                        rs.getString("created_at_text")),
                customerCode);
    }


    private int bool(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private Integer intOrNull(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private BigDecimal parseMoney(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim()
                .replace(",", "")
                .replace("\uFFE5", "")
                .replace("\u00A5", "");
        if (!normalized.matches("^-?\\d+(\\.\\d+)?$")) {
            return null;
        }
        return new BigDecimal(normalized);
    }

    private java.sql.Date parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(value.trim()));
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private Timestamp parseDateBoundary(String value, boolean endOfDay) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() == 10) {
            try {
                LocalDate date = LocalDate.parse(normalized);
                return Timestamp.valueOf(endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay());
            } catch (DateTimeParseException error) {
                return null;
            }
        }
        Timestamp parsed = parseDateTime(normalized);
        if (parsed == null || !endOfDay || normalized.length() > 10) {
            return parsed;
        }
        return Timestamp.valueOf(parsed.toLocalDateTime().toLocalDate().atTime(23, 59, 59));
    }

    private Timestamp parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        try {
            if (normalized.length() == 10) {
                return Timestamp.valueOf(LocalDate.parse(normalized).atStartOfDay());
            }
            if (normalized.length() == 16) {
                return Timestamp.valueOf(LocalDateTime.parse(normalized + ":00", DateTimeFormatterWithSeconds.HOLDER));
            }
            if (normalized.length() == 19) {
                return Timestamp.valueOf(LocalDateTime.parse(normalized, DateTimeFormatterWithSeconds.HOLDER));
            }
            return Timestamp.valueOf(LocalDateTime.of(LocalDate.parse(normalized), LocalTime.MIN));
        } catch (DateTimeParseException | IllegalArgumentException error) {
            return null;
        }
    }

    private static final class DateTimeFormatterWithSeconds {
        private static final java.time.format.DateTimeFormatter HOLDER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
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
