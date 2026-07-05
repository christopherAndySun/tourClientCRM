package com.tourcrm.service;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSchemaManager {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaManager(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSchema() {
        createTables();
        addMissingColumns();
        tightenColumnTypesWhenSafe();
        backfillTypedColumnsSafely();
        addMissingIndexes();
        backfillCustomerProfiles();
        dropLegacyPayloadColumns();
    }

    private void createTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_users (
                  employee_code VARCHAR(32) PRIMARY KEY,
                  name VARCHAR(80) NOT NULL,
                  password VARCHAR(255) NULL,
                  role VARCHAR(32) NOT NULL,
                  position VARCHAR(32) NOT NULL,
                  leader_employee_code VARCHAR(32) NULL,
                  org_type VARCHAR(32) NOT NULL DEFAULT 'HEADQUARTERS',
                  branch_id VARCHAR(64) NULL,
                  branch_name VARCHAR(120) NULL,
                  created_at_text VARCHAR(32) NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_crm_users_role_position (role, position),
                  INDEX idx_crm_users_leader (leader_employee_code),
                  INDEX idx_crm_users_org (org_type, branch_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_user_menu_permissions (
                  employee_code VARCHAR(32) NOT NULL,
                  menu_code VARCHAR(64) NOT NULL,
                  PRIMARY KEY (employee_code, menu_code),
                  INDEX idx_crm_user_menu_menu (menu_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_clues (
                  customer_code VARCHAR(64) PRIMARY KEY,
                  source_platform VARCHAR(32) NULL,
                  add_method VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  contact_info VARCHAR(255) NULL,
                  contact_key VARCHAR(255) NULL,
                  has_wechat_id TINYINT NOT NULL DEFAULT 1,
                  uploader VARCHAR(80) NULL,
                  uploader_employee_code VARCHAR(32) NULL,
                  org_type VARCHAR(32) NOT NULL DEFAULT 'HEADQUARTERS',
                  branch_id VARCHAR(64) NULL,
                  branch_name VARCHAR(120) NULL,
                  status VARCHAR(32) NOT NULL,
                  remark TEXT NULL,
                  repeat_demand TINYINT NOT NULL DEFAULT 0,
                  original_customer_code VARCHAR(64) NULL,
                  demand_sequence INT NOT NULL DEFAULT 1,
                  assigned_sales VARCHAR(80) NULL,
                  assigned_sales_employee_code VARCHAR(32) NULL,
                  deposit_amount VARCHAR(64) NULL,
                  deposit_amount_value DECIMAL(12,2) NULL,
                  remaining_balance VARCHAR(64) NULL,
                  remaining_balance_value DECIMAL(12,2) NULL,
                  status_remark TEXT NULL,
                  refund_amount VARCHAR(64) NULL,
                  refund_amount_value DECIMAL(12,2) NULL,
                  refunded_at VARCHAR(32) NULL,
                  refunded_at_value DATETIME NULL,
                  landing_at VARCHAR(32) NULL,
                  landing_at_value DATETIME NULL,
                  landing_remark TEXT NULL,
                  created_at_text VARCHAR(32) NULL,
                  created_at_value DATETIME NULL,
                  updated_at_text VARCHAR(32) NULL,
                  updated_at_value DATETIME NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_crm_clues_created (created_at_text),
                  INDEX idx_crm_clues_status_created (status, created_at_text),
                  INDEX idx_crm_clues_add_method_created (add_method, created_at_text),
                  INDEX idx_crm_clues_uploader_created (uploader_employee_code, created_at_text),
                  INDEX idx_crm_clues_org_created (org_type, branch_id, created_at_text),
                  INDEX idx_crm_clues_sales_created (assigned_sales_employee_code, created_at_text),
                  INDEX idx_crm_clues_contact (contact_info),
                  INDEX idx_crm_clues_contact_key (contact_key),
                  INDEX idx_crm_clues_original (original_customer_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_clue_daily_sequences (
                  sequence_date DATE NOT NULL,
                  sequence_scope VARCHAR(96) NOT NULL,
                  last_sequence INT NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (sequence_date, sequence_scope)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_deal_daily_sequences (
                  sequence_date DATE NOT NULL,
                  sequence_scope VARCHAR(96) NOT NULL,
                  last_sequence INT NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  PRIMARY KEY (sequence_date, sequence_scope)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_contact_locks (
                  contact_key VARCHAR(255) PRIMARY KEY,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_customer_profiles (
                  root_customer_code VARCHAR(64) PRIMARY KEY,
                  primary_contact_key VARCHAR(255) NULL,
                  contact_info VARCHAR(255) NULL,
                  created_by_code VARCHAR(32) NULL,
                  created_at_text VARCHAR(32) NULL,
                  updated_at_text VARCHAR(32) NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_crm_customer_profiles_contact (primary_contact_key)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_customer_contacts (
                  contact_key VARCHAR(255) PRIMARY KEY,
                  root_customer_code VARCHAR(64) NOT NULL,
                  contact_info VARCHAR(255) NULL,
                  created_at_text VARCHAR(32) NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_crm_customer_contacts_root (root_customer_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_clue_images (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  customer_code VARCHAR(64) NOT NULL,
                  image_type VARCHAR(32) NOT NULL,
                  name VARCHAR(255) NULL,
                  url VARCHAR(1024) NULL,
                  uid VARCHAR(128) NULL,
                  sort_order INT NOT NULL DEFAULT 0,
                  INDEX idx_crm_clue_images_code_type (customer_code, image_type, sort_order)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_clue_status_history (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  customer_code VARCHAR(64) NOT NULL,
                  status VARCHAR(32) NULL,
                  status_text VARCHAR(80) NULL,
                  operator VARCHAR(80) NULL,
                  operator_code VARCHAR(32) NULL,
                  deposit_amount VARCHAR(64) NULL,
                  remark TEXT NULL,
                  created_at_text VARCHAR(32) NULL,
                  INDEX idx_crm_status_history_code_time (customer_code, created_at_text)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_clue_follow_records (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  customer_code VARCHAR(64) NOT NULL,
                  operator VARCHAR(80) NULL,
                  operator_code VARCHAR(32) NULL,
                  remark TEXT NULL,
                  created_at_text VARCHAR(32) NULL,
                  INDEX idx_crm_follow_code_time (customer_code, created_at_text)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_clue_assign_logs (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  customer_code VARCHAR(64) NOT NULL,
                  action VARCHAR(32) NULL,
                  action_text VARCHAR(80) NULL,
                  operator VARCHAR(80) NULL,
                  operator_code VARCHAR(32) NULL,
                  from_sales VARCHAR(80) NULL,
                  from_sales_employee_code VARCHAR(32) NULL,
                  to_sales VARCHAR(80) NULL,
                  to_sales_employee_code VARCHAR(32) NULL,
                  remark TEXT NULL,
                  created_at_text VARCHAR(32) NULL,
                  INDEX idx_crm_assign_logs_code_time (customer_code, created_at_text),
                  INDEX idx_crm_assign_logs_sales_time (to_sales_employee_code, created_at_text)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_clue_operation_logs (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  customer_code VARCHAR(64) NOT NULL,
                  action VARCHAR(32) NULL,
                  action_text VARCHAR(80) NULL,
                  operator VARCHAR(80) NULL,
                  operator_code VARCHAR(32) NULL,
                  field_name VARCHAR(64) NULL,
                  field_text VARCHAR(80) NULL,
                  old_value TEXT NULL,
                  new_value TEXT NULL,
                  created_at_text VARCHAR(32) NULL,
                  INDEX idx_crm_operation_logs_code_time (customer_code, created_at_text),
                  INDEX idx_crm_operation_logs_operator_time (operator_code, created_at_text)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_deals (
                  deal_code VARCHAR(64) PRIMARY KEY,
                  customer_code VARCHAR(64) NOT NULL,
                  customer_name VARCHAR(255) NULL,
                  deposit VARCHAR(64) NULL,
                  deposit_value DECIMAL(12,2) NULL,
                  remaining_balance VARCHAR(64) NULL,
                  remaining_balance_value DECIMAL(12,2) NULL,
                  booking_date VARCHAR(32) NULL,
                  booking_date_value DATE NULL,
                  add_wechat_date VARCHAR(32) NULL,
                  add_wechat_date_value DATE NULL,
                  quote_text TEXT NULL,
                  travel_date VARCHAR(32) NULL,
                  travel_date_value DATE NULL,
                  itinerary TEXT NULL,
                  deal_date VARCHAR(32) NULL,
                  deal_date_value DATE NULL,
                  deal_user VARCHAR(80) NULL,
                  deal_user_code VARCHAR(32) NULL,
                  total_deal_sequence INT NULL,
                  personal_deal_sequence INT NULL,
                  status VARCHAR(32) NOT NULL,
                  refund_amount VARCHAR(64) NULL,
                  refund_amount_value DECIMAL(12,2) NULL,
                  refund_remark TEXT NULL,
                  refunded_at VARCHAR(32) NULL,
                  refunded_at_value DATETIME NULL,
                  landing_at VARCHAR(32) NULL,
                  landing_at_value DATETIME NULL,
                  landing_remark TEXT NULL,
                  created_at_text VARCHAR(32) NULL,
                  created_at_value DATETIME NULL,
                  updated_at_text VARCHAR(32) NULL,
                  updated_at_value DATETIME NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_crm_deals_customer (customer_code),
                  INDEX idx_crm_deals_sales_date (deal_user_code, deal_date),
                  INDEX idx_crm_deals_status_date (status, deal_date)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_menus (
                  menu_code VARCHAR(64) PRIMARY KEY,
                  group_code VARCHAR(64) NOT NULL,
                  group_name VARCHAR(80) NULL,
                  name VARCHAR(80) NULL,
                  description VARCHAR(255) NULL,
                  path VARCHAR(255) NULL,
                  sort_no INT NOT NULL DEFAULT 0,
                  enabled TINYINT NOT NULL DEFAULT 1,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_crm_menus_group_sort (group_code, sort_no)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_system_settings (
                  id TINYINT PRIMARY KEY,
                  ocr_app_code VARCHAR(255) NULL,
                  ocr_app_secret VARCHAR(255) NULL,
                  remark TEXT NULL,
                  updated_at_text VARCHAR(32) NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_third_party_downloads (
                  customer_code VARCHAR(64) PRIMARY KEY,
                  downloaded_by VARCHAR(80) NULL,
                  downloaded_by_code VARCHAR(32) NULL,
                  downloaded_at_text VARCHAR(32) NULL,
                  downloaded_at_value DATETIME NULL,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                  INDEX idx_crm_third_party_downloads_time (downloaded_at_value),
                  INDEX idx_crm_third_party_downloads_operator (downloaded_by_code, downloaded_at_value)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS crm_login_sessions (
                  token_hash CHAR(64) PRIMARY KEY,
                  employee_code VARCHAR(32) NOT NULL,
                  expires_at TIMESTAMP NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  INDEX idx_crm_sessions_employee (employee_code),
                  INDEX idx_crm_sessions_expires (expires_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void addMissingColumns() {
        addColumnIfMissing("crm_users", "password", "password VARCHAR(255) NULL");
        addColumnIfMissing("crm_users", "org_type", "org_type VARCHAR(32) NOT NULL DEFAULT 'HEADQUARTERS'");
        addColumnIfMissing("crm_users", "branch_id", "branch_id VARCHAR(64) NULL");
        addColumnIfMissing("crm_users", "branch_name", "branch_name VARCHAR(120) NULL");
        addColumnIfMissing("crm_clues", "add_method", "add_method VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'");
        addColumnIfMissing("crm_clues", "contact_key", "contact_key VARCHAR(255) NULL");
        addColumnIfMissing("crm_clues", "has_wechat_id", "has_wechat_id TINYINT NOT NULL DEFAULT 1");
        addColumnIfMissing("crm_clues", "uploader", "uploader VARCHAR(80) NULL");
        addColumnIfMissing("crm_clues", "org_type", "org_type VARCHAR(32) NOT NULL DEFAULT 'HEADQUARTERS'");
        addColumnIfMissing("crm_clues", "branch_id", "branch_id VARCHAR(64) NULL");
        addColumnIfMissing("crm_clues", "branch_name", "branch_name VARCHAR(120) NULL");
        addColumnIfMissing("crm_clues", "remark", "remark TEXT NULL");
        addColumnIfMissing("crm_clues", "repeat_demand", "repeat_demand TINYINT NOT NULL DEFAULT 0");
        addColumnIfMissing("crm_clues", "original_customer_code", "original_customer_code VARCHAR(64) NULL");
        addColumnIfMissing("crm_clues", "demand_sequence", "demand_sequence INT NOT NULL DEFAULT 1");
        addColumnIfMissing("crm_clues", "assigned_sales", "assigned_sales VARCHAR(80) NULL");
        addColumnIfMissing("crm_clues", "deposit_amount", "deposit_amount VARCHAR(64) NULL");
        addColumnIfMissing("crm_clues", "deposit_amount_value", "deposit_amount_value DECIMAL(12,2) NULL");
        addColumnIfMissing("crm_clues", "remaining_balance", "remaining_balance VARCHAR(64) NULL");
        addColumnIfMissing("crm_clues", "remaining_balance_value", "remaining_balance_value DECIMAL(12,2) NULL");
        addColumnIfMissing("crm_clues", "status_remark", "status_remark TEXT NULL");
        addColumnIfMissing("crm_clues", "refund_amount", "refund_amount VARCHAR(64) NULL");
        addColumnIfMissing("crm_clues", "refund_amount_value", "refund_amount_value DECIMAL(12,2) NULL");
        addColumnIfMissing("crm_clues", "refunded_at", "refunded_at VARCHAR(32) NULL");
        addColumnIfMissing("crm_clues", "refunded_at_value", "refunded_at_value DATETIME NULL");
        addColumnIfMissing("crm_clues", "landing_at", "landing_at VARCHAR(32) NULL");
        addColumnIfMissing("crm_clues", "landing_at_value", "landing_at_value DATETIME NULL");
        addColumnIfMissing("crm_clues", "landing_remark", "landing_remark TEXT NULL");
        addColumnIfMissing("crm_clues", "created_at_value", "created_at_value DATETIME NULL");
        addColumnIfMissing("crm_clues", "updated_at_value", "updated_at_value DATETIME NULL");
        addColumnIfMissing("crm_deals", "deposit", "deposit VARCHAR(64) NULL");
        addColumnIfMissing("crm_deals", "deposit_value", "deposit_value DECIMAL(12,2) NULL");
        addColumnIfMissing("crm_deals", "remaining_balance", "remaining_balance VARCHAR(64) NULL");
        addColumnIfMissing("crm_deals", "remaining_balance_value", "remaining_balance_value DECIMAL(12,2) NULL");
        addColumnIfMissing("crm_deals", "booking_date", "booking_date VARCHAR(32) NULL");
        addColumnIfMissing("crm_deals", "booking_date_value", "booking_date_value DATE NULL");
        addColumnIfMissing("crm_deals", "add_wechat_date", "add_wechat_date VARCHAR(32) NULL");
        addColumnIfMissing("crm_deals", "add_wechat_date_value", "add_wechat_date_value DATE NULL");
        addColumnIfMissing("crm_deals", "quote_text", "quote_text TEXT NULL");
        addColumnIfMissing("crm_deals", "travel_date", "travel_date VARCHAR(32) NULL");
        addColumnIfMissing("crm_deals", "travel_date_value", "travel_date_value DATE NULL");
        addColumnIfMissing("crm_deals", "itinerary", "itinerary TEXT NULL");
        addColumnIfMissing("crm_deals", "deal_date_value", "deal_date_value DATE NULL");
        addColumnIfMissing("crm_deals", "deal_user", "deal_user VARCHAR(80) NULL");
        addColumnIfMissing("crm_deals", "total_deal_sequence", "total_deal_sequence INT NULL");
        addColumnIfMissing("crm_deals", "personal_deal_sequence", "personal_deal_sequence INT NULL");
        addColumnIfMissing("crm_deals", "refund_amount", "refund_amount VARCHAR(64) NULL");
        addColumnIfMissing("crm_deals", "refund_amount_value", "refund_amount_value DECIMAL(12,2) NULL");
        addColumnIfMissing("crm_deals", "refund_remark", "refund_remark TEXT NULL");
        addColumnIfMissing("crm_deals", "refunded_at", "refunded_at VARCHAR(32) NULL");
        addColumnIfMissing("crm_deals", "refunded_at_value", "refunded_at_value DATETIME NULL");
        addColumnIfMissing("crm_deals", "landing_at", "landing_at VARCHAR(32) NULL");
        addColumnIfMissing("crm_deals", "landing_at_value", "landing_at_value DATETIME NULL");
        addColumnIfMissing("crm_deals", "landing_remark", "landing_remark TEXT NULL");
        addColumnIfMissing("crm_deals", "created_at_value", "created_at_value DATETIME NULL");
        addColumnIfMissing("crm_deals", "updated_at_text", "updated_at_text VARCHAR(32) NULL");
        addColumnIfMissing("crm_deals", "updated_at_value", "updated_at_value DATETIME NULL");
        addColumnIfMissing("crm_menus", "group_name", "group_name VARCHAR(80) NULL");
        addColumnIfMissing("crm_menus", "name", "name VARCHAR(80) NULL");
        addColumnIfMissing("crm_menus", "description", "description VARCHAR(255) NULL");
        addColumnIfMissing("crm_menus", "path", "path VARCHAR(255) NULL");
        addColumnIfMissing("crm_system_settings", "ocr_app_code", "ocr_app_code VARCHAR(255) NULL");
        addColumnIfMissing("crm_system_settings", "ocr_app_secret", "ocr_app_secret VARCHAR(255) NULL");
        addColumnIfMissing("crm_system_settings", "remark", "remark TEXT NULL");
        addColumnIfMissing("crm_system_settings", "updated_at_text", "updated_at_text VARCHAR(32) NULL");
    }

    private void addMissingIndexes() {
        addIndexIfMissing("crm_users", "idx_crm_users_role_position", "CREATE INDEX idx_crm_users_role_position ON crm_users (role, position)");
        addIndexIfMissing("crm_users", "idx_crm_users_leader", "CREATE INDEX idx_crm_users_leader ON crm_users (leader_employee_code)");
        addIndexIfMissing("crm_users", "idx_crm_users_org", "CREATE INDEX idx_crm_users_org ON crm_users (org_type, branch_id)");
        addIndexIfMissing("crm_user_menu_permissions", "idx_crm_user_menu_menu", "CREATE INDEX idx_crm_user_menu_menu ON crm_user_menu_permissions (menu_code)");

        addIndexIfMissing("crm_clues", "idx_crm_clues_created", "CREATE INDEX idx_crm_clues_created ON crm_clues (created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_updated", "CREATE INDEX idx_crm_clues_updated ON crm_clues (updated_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_status_created", "CREATE INDEX idx_crm_clues_status_created ON crm_clues (status, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_uploader_created", "CREATE INDEX idx_crm_clues_uploader_created ON crm_clues (uploader_employee_code, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_org_created", "CREATE INDEX idx_crm_clues_org_created ON crm_clues (org_type, branch_id, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_sales_created", "CREATE INDEX idx_crm_clues_sales_created ON crm_clues (assigned_sales_employee_code, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_sales_status_created", "CREATE INDEX idx_crm_clues_sales_status_created ON crm_clues (assigned_sales_employee_code, status, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_uploader_status_created", "CREATE INDEX idx_crm_clues_uploader_status_created ON crm_clues (uploader_employee_code, status, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_contact", "CREATE INDEX idx_crm_clues_contact ON crm_clues (contact_info)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_contact_key", "CREATE INDEX idx_crm_clues_contact_key ON crm_clues (contact_key)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_original", "CREATE INDEX idx_crm_clues_original ON crm_clues (original_customer_code)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_public_pool", "CREATE INDEX idx_crm_clues_public_pool ON crm_clues (assigned_sales_employee_code, status, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_source_created", "CREATE INDEX idx_crm_clues_source_created ON crm_clues (source_platform, created_at_text)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_created_value", "CREATE INDEX idx_crm_clues_created_value ON crm_clues (created_at_value)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_status_created_value", "CREATE INDEX idx_crm_clues_status_created_value ON crm_clues (status, created_at_value)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_uploader_created_value", "CREATE INDEX idx_crm_clues_uploader_created_value ON crm_clues (uploader_employee_code, created_at_value)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_sales_created_value", "CREATE INDEX idx_crm_clues_sales_created_value ON crm_clues (assigned_sales_employee_code, created_at_value)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_refunded_value", "CREATE INDEX idx_crm_clues_refunded_value ON crm_clues (refunded_at_value)");
        addIndexIfMissing("crm_clues", "idx_crm_clues_landing_value", "CREATE INDEX idx_crm_clues_landing_value ON crm_clues (landing_at_value)");

        addIndexIfMissing("crm_clue_images", "idx_crm_clue_images_code_type", "CREATE INDEX idx_crm_clue_images_code_type ON crm_clue_images (customer_code, image_type, sort_order)");
        addIndexIfMissing("crm_clue_status_history", "idx_crm_status_history_code_time", "CREATE INDEX idx_crm_status_history_code_time ON crm_clue_status_history (customer_code, created_at_text)");
        addIndexIfMissing("crm_clue_follow_records", "idx_crm_follow_code_time", "CREATE INDEX idx_crm_follow_code_time ON crm_clue_follow_records (customer_code, created_at_text)");
        addIndexIfMissing("crm_clue_assign_logs", "idx_crm_assign_logs_code_time", "CREATE INDEX idx_crm_assign_logs_code_time ON crm_clue_assign_logs (customer_code, created_at_text)");
        addIndexIfMissing("crm_clue_assign_logs", "idx_crm_assign_logs_sales_time", "CREATE INDEX idx_crm_assign_logs_sales_time ON crm_clue_assign_logs (to_sales_employee_code, created_at_text)");
        addIndexIfMissing("crm_clue_assign_logs", "idx_crm_assign_logs_action_time", "CREATE INDEX idx_crm_assign_logs_action_time ON crm_clue_assign_logs (action, created_at_text)");
        addIndexIfMissing("crm_clue_operation_logs", "idx_crm_operation_logs_code_time", "CREATE INDEX idx_crm_operation_logs_code_time ON crm_clue_operation_logs (customer_code, created_at_text)");
        addIndexIfMissing("crm_clue_operation_logs", "idx_crm_operation_logs_operator_time", "CREATE INDEX idx_crm_operation_logs_operator_time ON crm_clue_operation_logs (operator_code, created_at_text)");
        addIndexIfMissing("crm_clue_operation_logs", "idx_crm_operation_logs_field_time", "CREATE INDEX idx_crm_operation_logs_field_time ON crm_clue_operation_logs (field_name, created_at_text)");

        addIndexIfMissing("crm_deals", "idx_crm_deals_customer", "CREATE INDEX idx_crm_deals_customer ON crm_deals (customer_code)");
        addIndexIfMissing("crm_deals", "idx_crm_deals_sales_date", "CREATE INDEX idx_crm_deals_sales_date ON crm_deals (deal_user_code, deal_date)");
        addIndexIfMissing("crm_deals", "idx_crm_deals_status_date", "CREATE INDEX idx_crm_deals_status_date ON crm_deals (status, deal_date)");
        addIndexIfMissing("crm_deals", "idx_crm_deals_customer_status", "CREATE INDEX idx_crm_deals_customer_status ON crm_deals (customer_code, status)");
        addIndexIfMissing("crm_deals", "idx_crm_deals_date", "CREATE INDEX idx_crm_deals_date ON crm_deals (deal_date)");
        addIndexIfMissing("crm_deals", "idx_crm_deals_sales_date_value", "CREATE INDEX idx_crm_deals_sales_date_value ON crm_deals (deal_user_code, deal_date_value)");
        addIndexIfMissing("crm_deals", "idx_crm_deals_status_date_value", "CREATE INDEX idx_crm_deals_status_date_value ON crm_deals (status, deal_date_value)");
        addIndexIfMissing("crm_deals", "idx_crm_deals_date_value", "CREATE INDEX idx_crm_deals_date_value ON crm_deals (deal_date_value)");

        addIndexIfMissing("crm_menus", "idx_crm_menus_group_sort", "CREATE INDEX idx_crm_menus_group_sort ON crm_menus (group_code, sort_no)");
        addIndexIfMissing("crm_login_sessions", "idx_crm_sessions_employee", "CREATE INDEX idx_crm_sessions_employee ON crm_login_sessions (employee_code)");
        addIndexIfMissing("crm_login_sessions", "idx_crm_sessions_expires", "CREATE INDEX idx_crm_sessions_expires ON crm_login_sessions (expires_at)");
    }

    private void tightenColumnTypesWhenSafe() {
        Long base64ImageCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM crm_clue_images
                WHERE url LIKE 'data:image/%'
                """, Long.class);
        if (base64ImageCount == null || base64ImageCount == 0) {
            jdbcTemplate.execute("ALTER TABLE crm_clue_images MODIFY url VARCHAR(1024) NULL");
        }
    }

    private void backfillTypedColumnsSafely() {
        jdbcTemplate.update("""
                UPDATE crm_clues
                SET contact_key = LOWER(REGEXP_REPLACE(TRIM(COALESCE(contact_info, '')), '\\\\s+', '')),
                    deposit_amount_value = CASE
                      WHEN REGEXP_REPLACE(COALESCE(deposit_amount, ''), '[^0-9.-]', '') REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(REGEXP_REPLACE(COALESCE(deposit_amount, ''), '[^0-9.-]', '') AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    remaining_balance_value = CASE
                      WHEN REGEXP_REPLACE(COALESCE(remaining_balance, ''), '[^0-9.-]', '') REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(REGEXP_REPLACE(COALESCE(remaining_balance, ''), '[^0-9.-]', '') AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    refund_amount_value = CASE
                      WHEN REGEXP_REPLACE(COALESCE(refund_amount, ''), '[^0-9.-]', '') REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(REGEXP_REPLACE(COALESCE(refund_amount, ''), '[^0-9.-]', '') AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    refunded_at_value = STR_TO_DATE(NULLIF(refunded_at, ''), '%Y-%m-%d %H:%i'),
                    landing_at_value = STR_TO_DATE(NULLIF(landing_at, ''), '%Y-%m-%d %H:%i'),
                    created_at_value = STR_TO_DATE(NULLIF(created_at_text, ''), '%Y-%m-%d %H:%i'),
                    updated_at_value = STR_TO_DATE(NULLIF(updated_at_text, ''), '%Y-%m-%d %H:%i')
                """);
        jdbcTemplate.update("""
                UPDATE crm_deals
                SET deposit_value = CASE
                      WHEN REGEXP_REPLACE(COALESCE(deposit, ''), '[^0-9.-]', '') REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(REGEXP_REPLACE(COALESCE(deposit, ''), '[^0-9.-]', '') AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    remaining_balance_value = CASE
                      WHEN REGEXP_REPLACE(COALESCE(remaining_balance, ''), '[^0-9.-]', '') REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(REGEXP_REPLACE(COALESCE(remaining_balance, ''), '[^0-9.-]', '') AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    refund_amount_value = CASE
                      WHEN REGEXP_REPLACE(COALESCE(refund_amount, ''), '[^0-9.-]', '') REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(REGEXP_REPLACE(COALESCE(refund_amount, ''), '[^0-9.-]', '') AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    booking_date_value = STR_TO_DATE(NULLIF(booking_date, ''), '%Y-%m-%d'),
                    add_wechat_date_value = STR_TO_DATE(NULLIF(add_wechat_date, ''), '%Y-%m-%d'),
                    travel_date_value = STR_TO_DATE(NULLIF(travel_date, ''), '%Y-%m-%d'),
                    deal_date_value = STR_TO_DATE(NULLIF(deal_date, ''), '%Y-%m-%d'),
                    refunded_at_value = STR_TO_DATE(NULLIF(refunded_at, ''), '%Y-%m-%d %H:%i'),
                    landing_at_value = STR_TO_DATE(NULLIF(landing_at, ''), '%Y-%m-%d %H:%i'),
                    created_at_value = STR_TO_DATE(NULLIF(created_at_text, ''), '%Y-%m-%d %H:%i'),
                    updated_at_value = STR_TO_DATE(NULLIF(updated_at_text, ''), '%Y-%m-%d %H:%i')
                """);
    }

    private void backfillTypedColumns() {
        jdbcTemplate.update("""
                UPDATE crm_clues
                SET contact_key = LOWER(REGEXP_REPLACE(TRIM(COALESCE(contact_info, '')), '\\\\s+', '')),
                    deposit_amount_value = CASE
                      WHEN TRIM(REPLACE(REPLACE(REPLACE(deposit_amount, ',', ''), '￥', ''), '¥', '')) REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(TRIM(REPLACE(REPLACE(REPLACE(deposit_amount, ',', ''), '￥', ''), '¥', '')) AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    remaining_balance_value = CASE
                      WHEN TRIM(REPLACE(REPLACE(REPLACE(remaining_balance, ',', ''), '￥', ''), '¥', '')) REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(TRIM(REPLACE(REPLACE(REPLACE(remaining_balance, ',', ''), '￥', ''), '¥', '')) AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    refund_amount_value = CASE
                      WHEN TRIM(REPLACE(REPLACE(REPLACE(refund_amount, ',', ''), '￥', ''), '¥', '')) REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(TRIM(REPLACE(REPLACE(REPLACE(refund_amount, ',', ''), '￥', ''), '¥', '')) AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    refunded_at_value = STR_TO_DATE(NULLIF(refunded_at, ''), '%Y-%m-%d %H:%i'),
                    landing_at_value = STR_TO_DATE(NULLIF(landing_at, ''), '%Y-%m-%d %H:%i'),
                    created_at_value = STR_TO_DATE(NULLIF(created_at_text, ''), '%Y-%m-%d %H:%i'),
                    updated_at_value = STR_TO_DATE(NULLIF(updated_at_text, ''), '%Y-%m-%d %H:%i')
                """);
        jdbcTemplate.update("""
                UPDATE crm_deals
                SET deposit_value = CASE
                      WHEN TRIM(REPLACE(REPLACE(REPLACE(deposit, ',', ''), '￥', ''), '¥', '')) REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(TRIM(REPLACE(REPLACE(REPLACE(deposit, ',', ''), '￥', ''), '¥', '')) AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    remaining_balance_value = CASE
                      WHEN TRIM(REPLACE(REPLACE(REPLACE(remaining_balance, ',', ''), '￥', ''), '¥', '')) REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(TRIM(REPLACE(REPLACE(REPLACE(remaining_balance, ',', ''), '￥', ''), '¥', '')) AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    refund_amount_value = CASE
                      WHEN TRIM(REPLACE(REPLACE(REPLACE(refund_amount, ',', ''), '￥', ''), '¥', '')) REGEXP '^-?[0-9]+(\\\\.[0-9]+)?$'
                      THEN CAST(TRIM(REPLACE(REPLACE(REPLACE(refund_amount, ',', ''), '￥', ''), '¥', '')) AS DECIMAL(12,2))
                      ELSE NULL
                    END,
                    booking_date_value = STR_TO_DATE(NULLIF(booking_date, ''), '%Y-%m-%d'),
                    add_wechat_date_value = STR_TO_DATE(NULLIF(add_wechat_date, ''), '%Y-%m-%d'),
                    travel_date_value = STR_TO_DATE(NULLIF(travel_date, ''), '%Y-%m-%d'),
                    deal_date_value = STR_TO_DATE(NULLIF(deal_date, ''), '%Y-%m-%d'),
                    refunded_at_value = STR_TO_DATE(NULLIF(refunded_at, ''), '%Y-%m-%d %H:%i'),
                    landing_at_value = STR_TO_DATE(NULLIF(landing_at, ''), '%Y-%m-%d %H:%i'),
                    created_at_value = STR_TO_DATE(NULLIF(created_at_text, ''), '%Y-%m-%d %H:%i'),
                    updated_at_value = STR_TO_DATE(NULLIF(updated_at_text, ''), '%Y-%m-%d %H:%i')
                """);
    }

    private void dropLegacyPayloadColumns() {
        dropColumnIfExists("crm_users", "payload");
        dropColumnIfExists("crm_clues", "payload");
        dropColumnIfExists("crm_deals", "payload");
        dropColumnIfExists("crm_menus", "payload");
        dropColumnIfExists("crm_system_settings", "payload");
    }

    private void backfillCustomerProfiles() {
        jdbcTemplate.execute("""
                INSERT INTO crm_customer_profiles (
                  root_customer_code, primary_contact_key, contact_info, created_by_code, created_at_text, updated_at_text
                )
                SELECT
                  CASE
                    WHEN original_customer_code IS NULL OR original_customer_code = '' THEN customer_code
                    ELSE original_customer_code
                  END,
                  contact_key,
                  contact_info,
                  uploader_employee_code,
                  created_at_text,
                  updated_at_text
                FROM crm_clues
                WHERE status <> 'DELETED'
                ON DUPLICATE KEY UPDATE
                  primary_contact_key = COALESCE(crm_customer_profiles.primary_contact_key, VALUES(primary_contact_key)),
                  contact_info = COALESCE(crm_customer_profiles.contact_info, VALUES(contact_info)),
                  updated_at_text = GREATEST(COALESCE(crm_customer_profiles.updated_at_text, ''), COALESCE(VALUES(updated_at_text), ''))
                """);
        jdbcTemplate.execute("""
                INSERT IGNORE INTO crm_customer_contacts (
                  contact_key, root_customer_code, contact_info, created_at_text
                )
                SELECT
                  contact_key,
                  CASE
                    WHEN original_customer_code IS NULL OR original_customer_code = '' THEN customer_code
                    ELSE original_customer_code
                  END,
                  contact_info,
                  created_at_text
                FROM crm_clues
                WHERE status <> 'DELETED'
                  AND contact_key IS NOT NULL
                  AND contact_key <> ''
                ORDER BY created_at_value ASC, created_at_text ASC, customer_code ASC
                """);
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
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
        if (count == null || count == 0) {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + definition);
        }
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

    private void addIndexIfMissing(String tableName, String indexName, String createSql) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.statistics
                        WHERE table_schema = DATABASE()
                          AND table_name = ?
                          AND index_name = ?
                        """,
                Integer.class,
                tableName,
                indexName);
        if (count == null || count == 0) {
            jdbcTemplate.execute(createSql);
        }
    }

}
