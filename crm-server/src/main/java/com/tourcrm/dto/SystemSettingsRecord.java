package com.tourcrm.dto;

public record SystemSettingsRecord(
        String ocrAppCode,
        String ocrAppSecret,
        String remark,
        String updatedAt
) {
}
