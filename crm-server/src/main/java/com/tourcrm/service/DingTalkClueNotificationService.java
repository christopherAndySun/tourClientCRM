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
import java.util.ArrayList;
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
        NotificationScope scope = resolveScope(clue, creator);
        if (scope == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    notifyAsync(scope);
                }
            });
            return;
        }
        notifyAsync(scope);
    }

    private NotificationScope resolveScope(ClueResponse clue, UserSession creator) {
        if (clue == null || creator == null) {
            return null;
        }
        if ("ADMIN".equalsIgnoreCase(clean(creator.role())) || "SALES".equalsIgnoreCase(clean(creator.position()))) {
            return null;
        }
        boolean branch = "BRANCH".equalsIgnoreCase(firstText(clue.orgType(), creator.orgType()));
        return new NotificationScope(branch, clean(firstText(clue.branchId(), creator.branchId())), clean(firstText(clue.branchName(), creator.branchName())));
    }

    private void notifyAsync(NotificationScope scope) {
        CompletableFuture.runAsync(() -> {
            try {
                sendTodaySummary(scope);
            } catch (Exception error) {
                log.warn("DingTalk clue summary notification failed", error);
            }
        });
    }

    private void sendTodaySummary(NotificationScope scope) throws JsonProcessingException {
        SystemSettingsRecord settings = systemSettingsService.getForSystem();
        String webhook = scope.branch() ? settings.dingtalkBranchClueWebhook() : settings.dingtalkHqClueWebhook();
        boolean enabled = scope.branch()
                ? Boolean.TRUE.equals(settings.dingtalkBranchClueEnabled())
                : Boolean.TRUE.equals(settings.dingtalkHqClueEnabled());
        if (!enabled || !StringUtils.hasText(webhook)) {
            return;
        }
        LocalDate today = LocalDate.now(SHANGHAI_ZONE);
        List<EmployeeClueCount> counts = queryTodayCounts(today, scope);
        if (counts.isEmpty()) {
            return;
        }
        String content = buildContent(today, counts, scope);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("keyword", "客资");
        payload.put("title", buildTitle(today, scope));
        payload.put("content", content);
        payload.put("message", content);
        payload.put("text", content);
        payload.put("total", counts.stream().mapToLong(EmployeeClueCount::count).sum());
        payload.put("scope", scope.branch() ? "BRANCH" : "HEADQUARTERS");
        payload.put("branchId", scope.branchId());
        payload.put("branchName", scope.branchName());
        payload.put("items", counts.stream()
                .map(item -> Map.of(
                        "employeeCode", item.employeeCode(),
                        "employeeName", item.employeeName(),
                        "count", item.count()
                ))
                .toList());
        postWebhook(webhook, objectMapper.writeValueAsString(payload));
    }

    private List<EmployeeClueCount> queryTodayCounts(LocalDate date, NotificationScope scope) {
        List<Object> params = new ArrayList<>();
        StringBuilder sql = new StringBuilder("""
                SELECT clue.uploader_employee_code,
                       COALESCE(NULLIF(clue.uploader, ''), clue.uploader_employee_code) AS uploader_name,
                       COUNT(*) AS total_count
                FROM crm_clues clue
                LEFT JOIN crm_users user ON user.employee_code = clue.uploader_employee_code
                WHERE clue.status <> 'DELETED'
                  AND clue.created_at_value >= ?
                  AND clue.created_at_value < ?
                  AND COALESCE(clue.uploader_employee_code, '') <> ''
                  AND clue.uploader_employee_code <> 'ADMIN'
                  AND COALESCE(user.position, 'OPERATION') <> 'SALES'
                """);
        params.add(java.sql.Timestamp.valueOf(date.atStartOfDay()));
        params.add(java.sql.Timestamp.valueOf(date.plusDays(1).atStartOfDay()));
        if (scope.branch()) {
            sql.append(" AND COALESCE(NULLIF(clue.org_type, ''), 'HEADQUARTERS') = 'BRANCH'");
            if (StringUtils.hasText(scope.branchId())) {
                sql.append(" AND clue.branch_id = ?");
                params.add(scope.branchId());
            }
        } else {
            sql.append(" AND COALESCE(NULLIF(clue.org_type, ''), 'HEADQUARTERS') <> 'BRANCH'");
        }
        sql.append("""
                GROUP BY clue.uploader_employee_code, uploader_name
                ORDER BY clue.uploader_employee_code
                """);
        return jdbcTemplate.query(sql.toString(),
                (rs, rowNum) -> new EmployeeClueCount(
                        rs.getString("uploader_employee_code"),
                        rs.getString("uploader_name"),
                        rs.getLong("total_count")
                ),
                params.toArray());
    }

    private String buildTitle(LocalDate date, NotificationScope scope) {
        String scopeText = scope.branch() ? firstText(scope.branchName(), "分公司") : "";
        return date.format(TITLE_DATE_FORMAT) + " " + scopeText + "客资数据";
    }

    private String buildContent(LocalDate date, List<EmployeeClueCount> counts, NotificationScope scope) {
        long total = counts.stream().mapToLong(EmployeeClueCount::count).sum();
        StringBuilder content = new StringBuilder();
        content.append(buildTitle(date, scope)).append("\n");
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

    private String firstText(String first, String second) {
        String cleanedFirst = clean(first);
        return cleanedFirst.isEmpty() ? clean(second) : cleanedFirst;
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }

    private record NotificationScope(boolean branch, String branchId, String branchName) {
    }

    private record EmployeeClueCount(String employeeCode, String employeeName, long count) {
    }
}
