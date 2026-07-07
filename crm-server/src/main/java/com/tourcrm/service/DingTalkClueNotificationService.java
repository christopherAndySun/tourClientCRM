package com.tourcrm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tourcrm.dto.ClueResponse;
import com.tourcrm.dto.SystemSettingsRecord;
import com.tourcrm.dto.UserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Service
public class DingTalkClueNotificationService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkClueNotificationService.class);
    private static final ZoneId SHANGHAI_ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TITLE_DATE_FORMAT = DateTimeFormatter.ofPattern("yy.M.d");

    private final JdbcTemplate jdbcTemplate;
    private final SystemSettingsService systemSettingsService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public DingTalkClueNotificationService(
            JdbcTemplate jdbcTemplate,
            SystemSettingsService systemSettingsService,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.systemSettingsService = systemSettingsService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public void notifyHqClueCreatedAfterCommit(ClueResponse clue, UserSession creator) {
        if (!shouldNotify(clue, creator)) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyAsync();
                }
            });
            return;
        }
        notifyAsync();
    }

    private boolean shouldNotify(ClueResponse clue, UserSession creator) {
        if (clue == null || creator == null) {
            return false;
        }
        if ("ADMIN".equals(creator.role()) || "SALES".equals(creator.position())) {
            return false;
        }
        return !"BRANCH".equalsIgnoreCase(clean(clue.orgType()));
    }

    private void notifyAsync() {
        CompletableFuture.runAsync(() -> {
            try {
                sendTodaySummary();
            } catch (Exception error) {
                log.warn("DingTalk clue summary notification failed", error);
            }
        });
    }

    private void sendTodaySummary() throws JsonProcessingException {
        SystemSettingsRecord settings = systemSettingsService.getForSystem();
        if (!Boolean.TRUE.equals(settings.dingtalkHqClueEnabled()) || !StringUtils.hasText(settings.dingtalkHqClueWebhook())) {
            return;
        }
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        List<EmployeeClueCount> counts = queryHqTodayCounts(today);
        if (counts.isEmpty()) {
            return;
        }
        String content = buildContent(today, counts);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", "客资数据");
        payload.put("title", today.format(TITLE_DATE_FORMAT) + " 客资数据");
        payload.put("content", content);
        payload.put("message", content);
        payload.put("text", content);
        payload.put("total", counts.stream().mapToLong(EmployeeClueCount::count).sum());
        payload.put("items", counts.stream()
                .map(item -> Map.of(
                        "employeeCode", item.employeeCode(),
                        "employeeName", item.employeeName(),
                        "count", item.count()
                ))
                .toList());
        postWebhook(settings.dingtalkHqClueWebhook(), objectMapper.writeValueAsString(payload));
    }

    private List<EmployeeClueCount> queryHqTodayCounts(LocalDate date) {
        return jdbcTemplate.query("""
                        SELECT clue.uploader_employee_code,
                               COALESCE(NULLIF(clue.uploader, ''), clue.uploader_employee_code) AS uploader_name,
                               COUNT(*) AS total_count
                        FROM crm_clues clue
                        LEFT JOIN crm_users user ON user.employee_code = clue.uploader_employee_code
                        WHERE clue.status <> 'DELETED'
                          AND clue.created_at_value >= ?
                          AND clue.created_at_value < ?
                          AND COALESCE(NULLIF(clue.org_type, ''), 'HEADQUARTERS') <> 'BRANCH'
                          AND COALESCE(clue.uploader_employee_code, '') <> ''
                          AND clue.uploader_employee_code <> 'ADMIN'
                          AND COALESCE(user.position, 'OPERATION') <> 'SALES'
                        GROUP BY clue.uploader_employee_code, uploader_name
                        ORDER BY clue.uploader_employee_code
                        """,
                (rs, rowNum) -> new EmployeeClueCount(
                        rs.getString("uploader_employee_code"),
                        rs.getString("uploader_name"),
                        rs.getLong("total_count")
                ),
                java.sql.Timestamp.valueOf(date.atStartOfDay()),
                java.sql.Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
    }

    private String buildContent(LocalDate date, List<EmployeeClueCount> counts) {
        long total = counts.stream().mapToLong(EmployeeClueCount::count).sum();
        StringBuilder content = new StringBuilder();
        content.append(date.format(TITLE_DATE_FORMAT)).append(" 客资数据\n");
        content.append("总客资 ").append(total).append(" 人\n\n");
        for (EmployeeClueCount item : counts) {
            content.append(item.employeeCode())
                    .append("（")
                    .append(item.employeeName())
                    .append("）：客资 ")
                    .append(item.count())
                    .append(" 人\n");
        }
        return content.toString().trim();
    }

    private void postWebhook(String webhook, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhook))
                .timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenAccept(response -> {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        log.warn("DingTalk clue summary notification returned status {}: {}", response.statusCode(), response.body());
                    }
                })
                .exceptionally(error -> {
                    log.warn("DingTalk clue summary notification request failed", error);
                    return null;
                });
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record EmployeeClueCount(String employeeCode, String employeeName, long count) {
    }
}
