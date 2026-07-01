package com.tourcrm.dto;

public record AssignLogReportRow(
        String customerCode,
        String sourcePlatform,
        String contactInfo,
        String status,
        String uploader,
        String uploaderEmployeeCode,
        String currentSales,
        String currentSalesEmployeeCode,
        String action,
        String actionText,
        String operator,
        String operatorCode,
        String fromSales,
        String fromSalesCode,
        String toSales,
        String toSalesCode,
        String remark,
        String createdAt
) {
}
