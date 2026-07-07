package com.tourcrm.service;

import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.PageResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class CustomerClueRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerClueRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
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
            params.add(status.trim().toUpperCase(Locale.ROOT));
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
                params.add(status.trim().toUpperCase(Locale.ROOT));
            }
        }
        return queryCluePage(where, params, page, pageSize);
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
        where.append(sales ? " AND assigned_sales_employee_code = ?" : " AND uploader_employee_code = ?");
        params.add(employeeCode);
        appendDateTimeRange(where, params, "created_at_value", startDate, endDate);
        return queryCluePage(where.toString(), params, page, pageSize);
    }

    PageResponse<ClueResponse> queryCluePage(String where, List<Object> params, Integer page, Integer pageSize) {
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

    String buildClueWhereSql(
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
            params.add(sourcePlatform.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(addMethod)) {
            where.append(" AND add_method = ?");
            params.add(addMethod.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            where.append(" AND status = ?");
            params.add(status.trim().toUpperCase(Locale.ROOT));
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

    void appendDateTimeRange(StringBuilder where, List<Object> params, String column, String startDate, String endDate) {
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

    void appendInClause(StringBuilder where, List<Object> params, String column, List<String> values) {
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

    private void appendLike(StringBuilder where, List<Object> params, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        where.append(" AND LOWER(").append(column).append(") LIKE ?");
        params.add(likeValue(value));
    }

    private String likeValue(String value) {
        return "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
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

    private Integer intOrNull(ResultSet rs, String columnName) throws SQLException {
        int value = rs.getInt(columnName);
        return rs.wasNull() ? null : value;
    }

    private static final class DateTimeFormatterWithSeconds {
        private static final java.time.format.DateTimeFormatter HOLDER = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    }
}
