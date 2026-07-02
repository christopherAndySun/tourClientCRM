package com.tourcrm.service;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Repository
public class LoginSessionRepository {

    private final JdbcTemplate jdbcTemplate;

    public LoginSessionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void createSession(String token, String employeeCode, LocalDateTime expiresAt, boolean singleLogin) {
        if (singleLogin) {
            jdbcTemplate.update("DELETE FROM crm_login_sessions WHERE employee_code = ?", employeeCode);
        }
        jdbcTemplate.update("DELETE FROM crm_login_sessions WHERE expires_at < CURRENT_TIMESTAMP");
        jdbcTemplate.update("""
                        INSERT INTO crm_login_sessions (token_hash, employee_code, expires_at)
                        VALUES (?, ?, ?)
                        """,
                hashToken(token), employeeCode, Timestamp.valueOf(expiresAt));
    }

    public Optional<String> findSessionEmployeeCode(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT employee_code FROM crm_login_sessions WHERE token_hash = ? AND expires_at >= CURRENT_TIMESTAMP",
                    String.class,
                    hashToken(token)
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public void deleteSessionsByEmployeeCode(String employeeCode) {
        jdbcTemplate.update("DELETE FROM crm_login_sessions WHERE employee_code = ?", employeeCode);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("Token 加密失败", error);
        }
    }
}
