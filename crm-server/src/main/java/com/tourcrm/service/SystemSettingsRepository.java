package com.tourcrm.service;

import com.tourcrm.dto.SystemSettingsRecord;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SystemSettingsRepository {

    private final JdbcTemplate jdbcTemplate;

    public SystemSettingsRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SystemSettingsRecord> readSystemSettings() {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT ocr_app_code, ocr_app_secret,
                                   dingtalk_hq_clue_webhook, dingtalk_hq_clue_enabled,
                                   dingtalk_branch_clue_webhook, dingtalk_branch_clue_enabled,
                                   remark, updated_at_text
                            FROM crm_system_settings
                            WHERE id = 1
                            """,
                    (rs, rowNum) -> new SystemSettingsRecord(
                            rs.getString("ocr_app_code"),
                            rs.getString("ocr_app_secret"),
                            rs.getString("dingtalk_hq_clue_webhook"),
                            rs.getInt("dingtalk_hq_clue_enabled") == 1,
                            rs.getString("dingtalk_branch_clue_webhook"),
                            rs.getInt("dingtalk_branch_clue_enabled") == 1,
                            rs.getString("remark"),
                            rs.getString("updated_at_text")
                    )));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public void writeSystemSettings(SystemSettingsRecord settings) {
        jdbcTemplate.update("""
                        INSERT INTO crm_system_settings (
                          id, ocr_app_code, ocr_app_secret,
                          dingtalk_hq_clue_webhook, dingtalk_hq_clue_enabled,
                          dingtalk_branch_clue_webhook, dingtalk_branch_clue_enabled,
                          remark, updated_at_text
                        )
                        VALUES (1, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          ocr_app_code = VALUES(ocr_app_code),
                          ocr_app_secret = VALUES(ocr_app_secret),
                          dingtalk_hq_clue_webhook = VALUES(dingtalk_hq_clue_webhook),
                          dingtalk_hq_clue_enabled = VALUES(dingtalk_hq_clue_enabled),
                          dingtalk_branch_clue_webhook = VALUES(dingtalk_branch_clue_webhook),
                          dingtalk_branch_clue_enabled = VALUES(dingtalk_branch_clue_enabled),
                          remark = VALUES(remark),
                          updated_at_text = VALUES(updated_at_text)
                        """,
                settings.ocrAppCode(),
                settings.ocrAppSecret(),
                settings.dingtalkHqClueWebhook(),
                Boolean.TRUE.equals(settings.dingtalkHqClueEnabled()) ? 1 : 0,
                settings.dingtalkBranchClueWebhook(),
                Boolean.TRUE.equals(settings.dingtalkBranchClueEnabled()) ? 1 : 0,
                settings.remark(),
                settings.updatedAt());
    }
}
