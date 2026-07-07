package com.tourcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.dto.OcrRecognizeResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;

@Repository
public class OcrCallLogRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public OcrCallLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<OcrLogRecord> findLatestByImageKey(String imageKey) {
        if (!StringUtils.hasText(imageKey)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                            SELECT image_key, image_url, status, candidates_json, full_text, error_message
                            FROM crm_ocr_call_logs
                            WHERE image_key = ?
                            ORDER BY id DESC
                            LIMIT 1
                            """,
                    (rs, rowNum) -> new OcrLogRecord(
                            rs.getString("image_key"),
                            rs.getString("image_url"),
                            rs.getString("status"),
                            readCandidates(rs.getString("candidates_json")),
                            rs.getString("full_text"),
                            rs.getString("error_message")
                    ),
                    imageKey));
        } catch (EmptyResultDataAccessException error) {
            return Optional.empty();
        }
    }

    public void record(
            String imageKey,
            String imageUrl,
            String status,
            List<String> candidates,
            String fullText,
            String errorMessage,
            String operatorCode,
            String operatorName
    ) {
        jdbcTemplate.update("""
                        INSERT INTO crm_ocr_call_logs (
                          image_key, image_url, status, candidates_json, full_text, error_message,
                          operator_code, operator_name, created_at
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                        """,
                imageKey,
                imageUrl,
                status,
                writeCandidates(candidates),
                fullText,
                errorMessage,
                operatorCode,
                operatorName);
    }

    private List<String> readCandidates(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST);
        } catch (JsonProcessingException error) {
            return List.of();
        }
    }

    private String writeCandidates(List<String> candidates) {
        try {
            return objectMapper.writeValueAsString(candidates == null ? List.of() : candidates);
        } catch (JsonProcessingException error) {
            return "[]";
        }
    }

    public record OcrLogRecord(
            String imageKey,
            String imageUrl,
            String status,
            List<String> candidates,
            String fullText,
            String errorMessage
    ) {
        OcrRecognizeResponse toResponse() {
            String message = "SUCCESS".equals(status)
                    ? "识别成功"
                    : "NO_MATCH".equals(status) ? "未识别到微信号或手机号" : errorMessage;
            return new OcrRecognizeResponse(candidates == null ? List.of() : candidates, fullText, message);
        }
    }
}
