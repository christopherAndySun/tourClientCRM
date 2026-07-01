package com.tourcrm.dto;

public record OperationLogRecord(
        String action,
        String actionText,
        String operator,
        String operatorCode,
        String field,
        String fieldText,
        String oldValue,
        String newValue,
        String createdAt
) {
}
