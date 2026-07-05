package com.tourcrm.service;

import com.tourcrm.dto.ClueResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Repository
public class CustomerProfileRepository {

    private final JdbcTemplate jdbcTemplate;

    public CustomerProfileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findRootCustomerCodeByContactKey(String contactKey) {
        if (!StringUtils.hasText(contactKey)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT root_customer_code FROM crm_customer_contacts WHERE contact_key = ?",
                    String.class,
                    contactKey
            ));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public void acquireContactLock(String contactKey) {
        if (!StringUtils.hasText(contactKey)) {
            return;
        }
        jdbcTemplate.update("INSERT IGNORE INTO crm_contact_locks (contact_key) VALUES (?)", contactKey);
        jdbcTemplate.queryForObject("SELECT contact_key FROM crm_contact_locks WHERE contact_key = ? FOR UPDATE", String.class, contactKey);
    }

    public void upsertCustomerProfile(ClueResponse row) {
        String contactKey = contactKey(row.contactInfo());
        String rootCustomerCode = rootCustomerCode(row);
        jdbcTemplate.update("""
                        INSERT INTO crm_customer_profiles (
                          root_customer_code, primary_contact_key, contact_info, created_by_code, created_at_text, updated_at_text
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                          primary_contact_key = COALESCE(NULLIF(VALUES(primary_contact_key), ''), primary_contact_key),
                          contact_info = COALESCE(NULLIF(VALUES(contact_info), ''), contact_info),
                          updated_at_text = VALUES(updated_at_text)
                        """,
                rootCustomerCode,
                nullIfBlank(contactKey),
                nullIfBlank(row.contactInfo()),
                row.uploaderEmployeeCode(),
                row.createdAt(),
                row.updatedAt());
        if (StringUtils.hasText(contactKey)) {
            jdbcTemplate.update("""
                            INSERT INTO crm_customer_contacts (contact_key, root_customer_code, contact_info, created_at_text)
                            VALUES (?, ?, ?, ?)
                            ON DUPLICATE KEY UPDATE
                              contact_info = VALUES(contact_info),
                              root_customer_code = root_customer_code
                            """,
                    contactKey,
                    rootCustomerCode,
                    row.contactInfo(),
                    row.createdAt());
        }
    }

    private String contactKey(String contactInfo) {
        return StringUtils.hasText(contactInfo) ? contactInfo.trim().replaceAll("\\s+", "").toLowerCase() : "";
    }

    private String rootCustomerCode(ClueResponse row) {
        return StringUtils.hasText(row.originalCustomerCode()) ? row.originalCustomerCode() : row.customerCode();
    }

    private String nullIfBlank(String value) {
        return StringUtils.hasText(value) ? value : null;
    }
}
