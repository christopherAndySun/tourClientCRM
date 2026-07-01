package com.tourcrm.dto;

public record AssignLogRecord(
        String action,
        String actionText,
        String operator,
        String operatorCode,
        String fromSales,
        String fromSalesEmployeeCode,
        String toSales,
        String toSalesEmployeeCode,
        String remark,
        String createdAt
) {
}
