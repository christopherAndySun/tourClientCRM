package com.tourcrm.service;

import com.tourcrm.dto.PageResponse;
import com.tourcrm.dto.SystemAuditLogRow;
import com.tourcrm.dto.UserSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class SystemAuditService {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final JdbcTemplate jdbcTemplate;
    private final AuthService authService;

    public SystemAuditService(JdbcTemplate jdbcTemplate, AuthService authService) {
        this.jdbcTemplate = jdbcTemplate;
        this.authService = authService;
    }

    public void record(String token, String action, String actionText, String targetType, String targetCode, String remark) {
        UserSession user = authService.currentUser(token);
        record(user.name(), user.employeeCode(), action, actionText, targetType, targetCode, remark);
    }

    public void recordUser(String operator, String operatorCode, String action, String actionText, String targetType, String targetCode, String remark) {
        record(operator, operatorCode, action, actionText, targetType, targetCode, remark);
    }

    public PageResponse<SystemAuditLogRow> page(
            String action,
            String operator,
            String targetType,
            String targetCode,
            String startDate,
            String endDate,
            Integer page,
            Integer pageSize,
            String token
    ) {
        authService.requireAdminUser(token);
        List<Object> params = new ArrayList<>();
        StringBuilder where = new StringBuilder("1 = 1");
        if (StringUtils.hasText(action)) {
            where.append(" AND action = ?");
            params.add(action.trim().toUpperCase());
        }
        if (StringUtils.hasText(operator)) {
            where.append(" AND (LOWER(operator) LIKE ? OR LOWER(operator_code) LIKE ?)");
            String value = likeValue(operator);
            params.add(value);
            params.add(value);
        }
        if (StringUtils.hasText(targetType)) {
            where.append(" AND target_type = ?");
            params.add(targetType.trim().toUpperCase());
        }
        if (StringUtils.hasText(targetCode)) {
            where.append(" AND LOWER(target_code) LIKE ?");
            params.add(likeValue(targetCode));
        }
        appendDateRange(where, params, startDate, endDate);

        int safePage = page == null || page < 1 ? 1 : page;
        int safePageSize = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM crm_system_audit_logs WHERE " + where, Long.class, params.toArray());
        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add((safePage - 1) * safePageSize);
        pageParams.add(safePageSize);
        List<SystemAuditLogRow> rows = jdbcTemplate.query("""
                        SELECT action, action_text, operator, operator_code, target_type, target_code, remark, created_at_text
                        FROM crm_system_audit_logs
                        """ + "WHERE " + where + """
                        ORDER BY created_at_value DESC, id DESC
                        LIMIT ?, ?
                        """,
                (rs, rowNum) -> new SystemAuditLogRow(
                        rs.getString("action"),
                        rs.getString("action_text"),
                        rs.getString("operator"),
                        rs.getString("operator_code"),
                        rs.getString("target_type"),
                        rs.getString("target_code"),
                        rs.getString("remark"),
                        rs.getString("created_at_text")
                ),
                pageParams.toArray());
        return new PageResponse<>(rows, total, safePage, safePageSize, (long) safePage * safePageSize < total);
    }

    private void record(String operator, String operatorCode, String action, String actionText, String targetType, String targetCode, String remark) {
        jdbcTemplate.update("""
                        INSERT INTO crm_system_audit_logs (
                          action, action_text, operator, operator_code, target_type, target_code, remark, created_at_text, created_at_value
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                clean(action),
                clean(actionText),
                clean(operator),
                clean(operatorCode),
                clean(targetType),
                clean(targetCode),
                clean(remark),
                nowText(),
                java.sql.Timestamp.valueOf(LocalDateTime.now())
        );
    }

    private String nowText() {
        return LocalDateTime.now().format(DATE_TIME_FORMAT);
    }

    private String clean(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String likeValue(String value) {
        return "%" + value.trim().toLowerCase() + "%";
    }

    private void appendDateRange(StringBuilder where, List<Object> params, String startDate, String endDate) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        if (start != null) {
            where.append(" AND created_at_value >= ?");
            params.add(Timestamp.valueOf(start.atStartOfDay()));
        }
        if (end != null) {
            where.append(" AND created_at_value <= ?");
            params.add(Timestamp.valueOf(end.atTime(LocalTime.MAX)));
        }
    }

    private LocalDate parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (RuntimeException error) {
            return null;
        }
    }
}
