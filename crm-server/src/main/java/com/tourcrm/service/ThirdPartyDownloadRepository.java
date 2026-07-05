package com.tourcrm.service;

import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.ThirdPartyDownloadFailureRow;
import com.tourcrm.dto.ThirdPartyDownloadResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Repository
public class ThirdPartyDownloadRepository {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final JdbcTemplate jdbcTemplate;

    public ThirdPartyDownloadRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<ThirdPartyDownloadResponse> queryPage(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            boolean downloaded,
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
        String where = buildDownloadWhereSql(visibleUploaderCodes, visibleSalesCodes, keyword, customerCode, contactInfo, sourcePlatform, addMethod, status, uploader, assignedSales, startDate, endDate, params);
        where += downloaded
                ? " AND EXISTS (SELECT 1 FROM crm_third_party_downloads tpd WHERE tpd.customer_code = crm_clues.customer_code)"
                : " AND NOT EXISTS (SELECT 1 FROM crm_third_party_downloads tpd WHERE tpd.customer_code = crm_clues.customer_code)";
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_clues WHERE " + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        String orderBy = downloaded
                ? "ORDER BY (SELECT tpd.downloaded_at_value FROM crm_third_party_downloads tpd WHERE tpd.customer_code = crm_clues.customer_code) DESC, customer_code DESC"
                : "ORDER BY COALESCE(updated_at_value, created_at_value) DESC, customer_code DESC";
        List<ThirdPartyDownloadResponse> rows = jdbcTemplate.query("""
                SELECT customer_code, source_platform, add_method, contact_info, has_wechat_id, uploader, uploader_employee_code,
                       org_type, branch_id, branch_name, status, remark, repeat_demand, original_customer_code, demand_sequence, assigned_sales,
                       assigned_sales_employee_code, deposit_amount, remaining_balance, status_remark, refund_amount, refunded_at,
                       landing_at, landing_remark, created_at_text, updated_at_text,
                       (SELECT tpd.downloaded_by FROM crm_third_party_downloads tpd WHERE tpd.customer_code = crm_clues.customer_code) AS downloaded_by,
                       (SELECT tpd.downloaded_by_code FROM crm_third_party_downloads tpd WHERE tpd.customer_code = crm_clues.customer_code) AS downloaded_by_code,
                       (SELECT tpd.downloaded_at_text FROM crm_third_party_downloads tpd WHERE tpd.customer_code = crm_clues.customer_code) AS downloaded_at_text
                FROM crm_clues
                """ + "WHERE " + where + "\n" + orderBy + "\n" + """
                LIMIT ?, ?
                """,
                (rs, rowNum) -> new ThirdPartyDownloadResponse(readClueListRow(rs), rs.getString("downloaded_by"), rs.getString("downloaded_by_code"), rs.getString("downloaded_at_text")),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    public boolean markDownloaded(String customerCode, String downloadedBy, String downloadedByCode, String downloadedAt) {
        int existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_clues WHERE customer_code = ? AND status <> 'DELETED'", Integer.class, customerCode);
        if (existing < 1) {
            return false;
        }
        jdbcTemplate.update("""
                        INSERT IGNORE INTO crm_third_party_downloads
                          (customer_code, downloaded_by, downloaded_by_code, downloaded_at_text, downloaded_at_value)
                        VALUES (?, ?, ?, ?, ?)
                        """,
                customerCode,
                downloadedBy,
                downloadedByCode,
                downloadedAt,
                parseDateTime(downloadedAt)
        );
        return true;
    }

    public boolean restorePending(String customerCode) {
        int affected = jdbcTemplate.update("DELETE FROM crm_third_party_downloads WHERE customer_code = ?", customerCode);
        return affected > 0;
    }

    public void recordLog(String customerCode, String action, String actionText, String operator, String operatorCode, String remark, String createdAt) {
        jdbcTemplate.update("""
                        INSERT INTO crm_third_party_download_logs (
                          customer_code, action, action_text, operator, operator_code, remark, created_at_text, created_at_value
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                customerCode,
                action,
                actionText,
                operator,
                operatorCode,
                remark,
                createdAt,
                parseDateTime(createdAt)
        );
    }

    public PageResponse<ThirdPartyDownloadFailureRow> queryFailurePage(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String customerCode,
            String operator,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildFailureWhereSql(visibleUploaderCodes, visibleSalesCodes, customerCode, operator, startDate, endDate, params);
        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long total = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crm_third_party_download_logs log
                JOIN crm_clues clue ON clue.customer_code = log.customer_code
                WHERE """ + where,
                Long.class,
                params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        List<ThirdPartyDownloadFailureRow> rows = jdbcTemplate.query("""
                SELECT log.customer_code, clue.contact_info, clue.source_platform, clue.add_method, clue.status,
                       clue.uploader, clue.uploader_employee_code, clue.assigned_sales, clue.assigned_sales_employee_code,
                       log.operator, log.operator_code, log.remark, log.created_at_text
                FROM crm_third_party_download_logs log
                JOIN crm_clues clue ON clue.customer_code = log.customer_code
                WHERE """ + where + """
                ORDER BY log.created_at_value DESC, log.id DESC
                LIMIT ?, ?
                """,
                (rs, rowNum) -> readFailureRow(rs),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    public List<ThirdPartyDownloadFailureRow> queryFailuresForExport(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String customerCode,
            String operator,
            String startDate,
            String endDate,
            int limit
    ) {
        List<Object> params = new ArrayList<>();
        String where = buildFailureWhereSql(visibleUploaderCodes, visibleSalesCodes, customerCode, operator, startDate, endDate, params);
        params.add(Math.max(1, Math.min(limit, 50000)));
        return jdbcTemplate.query("""
                SELECT log.customer_code, clue.contact_info, clue.source_platform, clue.add_method, clue.status,
                       clue.uploader, clue.uploader_employee_code, clue.assigned_sales, clue.assigned_sales_employee_code,
                       log.operator, log.operator_code, log.remark, log.created_at_text
                FROM crm_third_party_download_logs log
                JOIN crm_clues clue ON clue.customer_code = log.customer_code
                WHERE """ + where + """
                ORDER BY log.created_at_value DESC, log.id DESC
                LIMIT ?
                """,
                (rs, rowNum) -> readFailureRow(rs),
                params.toArray());
    }

    private String buildDownloadWhereSql(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
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
        appendScope(where, params, visibleUploaderCodes, visibleSalesCodes);
        appendLike(where, params, "customer_code", customerCode);
        appendLike(where, params, "contact_info", contactInfo);
        appendLike(where, params, "uploader", uploader);
        appendLike(where, params, "assigned_sales", assignedSales);
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
        if (StringUtils.hasText(keyword)) {
            where.append(" AND (customer_code LIKE ? OR contact_info LIKE ? OR remark LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        appendDateRange(where, params, "created_at_value", startDate, endDate);
        return where.toString();
    }

    private String buildFailureWhereSql(
            List<String> visibleUploaderCodes,
            List<String> visibleSalesCodes,
            String customerCode,
            String operator,
            String startDate,
            String endDate,
            List<Object> params
    ) {
        StringBuilder where = new StringBuilder("log.action = 'DOWNLOAD_FAILED' AND clue.status <> 'DELETED'");
        appendScope(where, params, visibleUploaderCodes, visibleSalesCodes);
        appendLike(where, params, "log.customer_code", customerCode);
        appendLike(where, params, "log.operator", operator);
        appendDateRange(where, params, "log.created_at_value", startDate, endDate);
        return where.toString();
    }

    private void appendScope(StringBuilder where, List<Object> params, List<String> visibleUploaderCodes, List<String> visibleSalesCodes) {
        if (visibleUploaderCodes != null) {
            if (visibleUploaderCodes.isEmpty()) {
                where.append(" AND 1 = 0");
            } else {
                appendInClause(where, params, "uploader_employee_code", visibleUploaderCodes);
            }
        }
        if (visibleSalesCodes != null) {
            if (visibleSalesCodes.isEmpty()) {
                where.append(" AND 1 = 0");
            } else {
                appendInClause(where, params, "assigned_sales_employee_code", visibleSalesCodes);
            }
        }
    }

    private void appendInClause(StringBuilder where, List<Object> params, String column, List<String> values) {
        where.append(" AND ").append(column).append(" IN (");
        where.append("?,".repeat(values.size()));
        where.setLength(where.length() - 1);
        where.append(")");
        params.addAll(values);
    }

    private void appendLike(StringBuilder where, List<Object> params, String column, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        where.append(" AND ").append(column).append(" LIKE ?");
        params.add("%" + value.trim() + "%");
    }

    private void appendDateRange(StringBuilder where, List<Object> params, String column, String startDate, String endDate) {
        java.sql.Date start = parseDate(startDate);
        if (start != null) {
            where.append(" AND ").append(column).append(" >= ?");
            params.add(start);
        }
        java.sql.Date end = parseDate(endDate);
        if (end != null) {
            where.append(" AND ").append(column).append(" < DATE_ADD(?, INTERVAL 1 DAY)");
            params.add(end);
        }
    }

    private ClueResponse readClueListRow(ResultSet rs) throws SQLException {
        return new ClueResponse(
                rs.getString("customer_code"),
                rs.getString("source_platform"),
                rs.getString("add_method"),
                rs.getString("contact_info"),
                rs.getBoolean("has_wechat_id"),
                rs.getString("uploader"),
                rs.getString("uploader_employee_code"),
                rs.getString("org_type"),
                rs.getString("branch_id"),
                rs.getString("branch_name"),
                rs.getString("status"),
                rs.getString("remark"),
                List.of(),
                List.of(),
                rs.getBoolean("repeat_demand"),
                rs.getString("original_customer_code"),
                rs.getInt("demand_sequence"),
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

    private ThirdPartyDownloadFailureRow readFailureRow(ResultSet rs) throws SQLException {
        return new ThirdPartyDownloadFailureRow(
                rs.getString("customer_code"),
                rs.getString("contact_info"),
                rs.getString("source_platform"),
                rs.getString("add_method"),
                rs.getString("status"),
                rs.getString("uploader"),
                rs.getString("uploader_employee_code"),
                rs.getString("assigned_sales"),
                rs.getString("assigned_sales_employee_code"),
                rs.getString("operator"),
                rs.getString("operator_code"),
                rs.getString("remark"),
                rs.getString("created_at_text")
        );
    }

    private java.sql.Date parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return java.sql.Date.valueOf(LocalDate.parse(value.trim(), DATE_FORMAT));
        } catch (DateTimeParseException error) {
            return null;
        }
    }

    private Timestamp parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            String normalized = value.trim().replace("T", " ");
            if (normalized.length() == 10) {
                return Timestamp.valueOf(LocalDate.parse(normalized, DATE_FORMAT).atStartOfDay());
            }
            return Timestamp.valueOf(LocalDateTime.parse(normalized, DATE_TIME_FORMAT));
        } catch (DateTimeParseException error) {
            return null;
        }
    }
}
