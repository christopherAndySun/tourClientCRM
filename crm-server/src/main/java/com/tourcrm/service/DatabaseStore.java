package com.tourcrm.service;

import com.tourcrm.dto.AssignLogRecord;
import com.tourcrm.dto.AssignLogReportRow;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.ClueStatsResponse;
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
