package com.tourcrm.dto;

public record OperationLogReportRow(
        String customerCode,
        String sourcePlatform,
        String contactInfo,
        String status,
        String uploader,
        String uploaderEmployeeCode,
        String assignedSales,
        String assignedSalesEmployeeCode,
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
