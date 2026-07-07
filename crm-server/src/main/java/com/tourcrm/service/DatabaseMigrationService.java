package com.tourcrm.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DatabaseMigrationService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseMigrationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void runMaintenanceMigrations() {
        cleanupOrphanChildRows();
        addMissingForeignKeys();
        dropLegacyPayloadColumns();
    }

    private void cleanupOrphanChildRows() {
        jdbcTemplate.update("DELETE child FROM crm_clue_images child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_clue_status_history child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_clue_follow_records child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_clue_assign_logs child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_clue_operation_logs child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_deals child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_third_party_downloads child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_third_party_download_logs child LEFT JOIN crm_clues parent ON parent.customer_code = child.customer_code WHERE parent.customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_customer_contacts child LEFT JOIN crm_customer_profiles parent ON parent.root_customer_code = child.root_customer_code WHERE parent.root_customer_code IS NULL");
        jdbcTemplate.update("DELETE child FROM crm_customer_profiles child LEFT JOIN crm_clues parent ON parent.customer_code = child.root_customer_code WHERE parent.customer_code IS NULL");
    }

    private void addMissingForeignKeys() {
        addForeignKeyIfMissing("crm_clue_images", "fk_crm_clue_images_customer", "ALTER TABLE crm_clue_images ADD CONSTRAINT fk_crm_clue_images_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_clue_status_history", "fk_crm_status_history_customer", "ALTER TABLE crm_clue_status_history ADD CONSTRAINT fk_crm_status_history_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_clue_follow_records", "fk_crm_follow_records_customer", "ALTER TABLE crm_clue_follow_records ADD CONSTRAINT fk_crm_follow_records_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_clue_assign_logs", "fk_crm_assign_logs_customer", "ALTER TABLE crm_clue_assign_logs ADD CONSTRAINT fk_crm_assign_logs_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_clue_operation_logs", "fk_crm_operation_logs_customer", "ALTER TABLE crm_clue_operation_logs ADD CONSTRAINT fk_crm_operation_logs_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_deals", "fk_crm_deals_customer", "ALTER TABLE crm_deals ADD CONSTRAINT fk_crm_deals_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE RESTRICT");
        addForeignKeyIfMissing("crm_third_party_downloads", "fk_crm_third_party_downloads_customer", "ALTER TABLE crm_third_party_downloads ADD CONSTRAINT fk_crm_third_party_downloads_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_third_party_download_logs", "fk_crm_third_party_logs_customer", "ALTER TABLE crm_third_party_download_logs ADD CONSTRAINT fk_crm_third_party_logs_customer FOREIGN KEY (customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_customer_profiles", "fk_crm_customer_profiles_root", "ALTER TABLE crm_customer_profiles ADD CONSTRAINT fk_crm_customer_profiles_root FOREIGN KEY (root_customer_code) REFERENCES crm_clues(customer_code) ON DELETE CASCADE");
        addForeignKeyIfMissing("crm_customer_contacts", "fk_crm_customer_contacts_root", "ALTER TABLE crm_customer_contacts ADD CONSTRAINT fk_crm_customer_contacts_root FOREIGN KEY (root_customer_code) REFERENCES crm_customer_profiles(root_customer_code) ON DELETE CASCADE");
    }

    private void dropLegacyPayloadColumns() {
        dropColumnIfExists("crm_users", "payload");
        dropColumnIfExists("crm_clues", "payload");
        dropColumnIfExists("crm_deals", "payload");
        dropColumnIfExists("crm_menus", "payload");
        dropColumnIfExists("crm_system_settings", "payload");
    }

    private void dropColumnIfExists(String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.columns
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND column_name = ?
                        """,
                Integer.class,
                tableName,
                columnName);
        if (count != null && count > 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
        }
    }

    private void addForeignKeyIfMissing(String tableName, String constraintName, String alterSql) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.table_constraints
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND constraint_name = ?
                          AND constraint_type = 'FOREIGN KEY'
                        """,
                Integer.class,
                tableName,
                constraintName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(alterSql);
        }
    }
}
