CREATE DATABASE IF NOT EXISTS tour_client_crm
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE tour_client_crm;

CREATE TABLE sys_group (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  group_name VARCHAR(64) NOT NULL,
  leader_user_id BIGINT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_group_name (group_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sys_user (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  name VARCHAR(64) NOT NULL,
  employee_code VARCHAR(8) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(24) NOT NULL DEFAULT 'EMPLOYEE',
  position VARCHAR(24) NOT NULL DEFAULT 'OPERATION',
  group_id BIGINT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_employee_code (employee_code),
  KEY idx_user_group_id (group_id),
  CONSTRAINT fk_user_group FOREIGN KEY (group_id) REFERENCES sys_group(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE sys_group
  ADD CONSTRAINT fk_group_leader FOREIGN KEY (leader_user_id) REFERENCES sys_user(id);

CREATE TABLE customer_clue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_code VARCHAR(32) NOT NULL,
  contact_info VARCHAR(128) NOT NULL,
  douyin_image_url VARCHAR(500) NULL,
  wechat_image_url VARCHAR(500) NULL,
  remark VARCHAR(1000) NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'NEW',
  uploader_user_id BIGINT NOT NULL,
  current_owner_user_id BIGINT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted TINYINT NOT NULL DEFAULT 0,
  UNIQUE KEY uk_customer_code (customer_code),
  KEY idx_clue_created_at (created_at),
  KEY idx_clue_status (status),
  KEY idx_clue_uploader (uploader_user_id),
  CONSTRAINT fk_clue_uploader FOREIGN KEY (uploader_user_id) REFERENCES sys_user(id),
  CONSTRAINT fk_clue_owner FOREIGN KEY (current_owner_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE customer_deal (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_clue_id BIGINT NOT NULL,
  sales_user_id BIGINT NOT NULL,
  customer_name VARCHAR(64) NOT NULL,
  deposit VARCHAR(128) NOT NULL,
  booking_date DATE NULL,
  add_wechat_date DATE NULL,
  quote_text VARCHAR(500) NULL,
  travel_date VARCHAR(128) NULL,
  itinerary VARCHAR(1000) NULL,
  deal_date DATE NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_deal_clue (customer_clue_id),
  KEY idx_deal_sales_date (sales_user_id, deal_date),
  CONSTRAINT fk_deal_clue FOREIGN KEY (customer_clue_id) REFERENCES customer_clue(id),
  CONSTRAINT fk_deal_sales FOREIGN KEY (sales_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE word_template (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  template_name VARCHAR(80) NOT NULL,
  template_file_path VARCHAR(500) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

