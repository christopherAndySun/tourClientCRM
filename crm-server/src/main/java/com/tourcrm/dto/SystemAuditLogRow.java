package com.tourcrm.dto;

public record SystemAuditLogRow(
        String action,
        String actionText,
        String operator,
        String operatorCode,
        String targetType,
        String targetCode,
        String remark,
        String createdAt
) {
}
