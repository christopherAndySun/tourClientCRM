package com.tourcrm.service;

import com.tourcrm.dto.UserSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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
}
