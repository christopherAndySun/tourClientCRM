package com.tourcrm.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Locale;

@Repository
public class SequenceRepository {

    private final JdbcTemplate jdbcTemplate;

    public SequenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public int nextClueDailySequence(LocalDate date, String sequenceScope) {
        return nextDailySequence("crm_clue_daily_sequences", date, sequenceScope, "HQ");
    }

    @Transactional
    public int nextDealDailySequence(LocalDate date, String sequenceScope) {
        return nextDailySequence("crm_deal_daily_sequences", date, sequenceScope, "TOTAL");
    }

    private int nextDailySequence(String tableName, LocalDate date, String sequenceScope, String defaultScope) {
        String scope = StringUtils.hasText(sequenceScope) ? sequenceScope.trim().toUpperCase(Locale.ROOT) : defaultScope;
        jdbcTemplate.update("""
                        INSERT INTO %s (sequence_date, sequence_scope, last_sequence)
                        VALUES (?, ?, LAST_INSERT_ID(1))
                        ON DUPLICATE KEY UPDATE last_sequence = LAST_INSERT_ID(last_sequence + 1)
                        """.formatted(tableName),
                java.sql.Date.valueOf(date),
                scope);
        Long sequence = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        return sequence == null ? 1 : sequence.intValue();
    }
}
