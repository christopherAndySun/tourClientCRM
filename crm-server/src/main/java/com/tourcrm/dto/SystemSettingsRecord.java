package com.tourcrm.dto;

public record SystemSettingsRecord(
        String ocrAppCode,
        String ocrAppSecret,
        String dingtalkHqClueWebhook,
        Boolean dingtalkHqClueEnabled,
        String remark,
        String updatedAt
) {
}
