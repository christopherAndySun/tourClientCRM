package com.tourcrm.dto;

public record SystemSettingsRecord(
        String ocrAppCode,
        String ocrAppSecret,
        String dingtalkHqClueWebhook,
        Boolean dingtalkHqClueEnabled,
        String dingtalkBranchClueWebhook,
        Boolean dingtalkBranchClueEnabled,
        String remark,
        String updatedAt
) {
}
