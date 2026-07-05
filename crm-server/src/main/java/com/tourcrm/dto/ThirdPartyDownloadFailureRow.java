package com.tourcrm.dto;

public record ThirdPartyDownloadFailureRow(
        String customerCode,
        String contactInfo,
        String sourcePlatform,
        String addMethod,
        String status,
        String uploader,
        String uploaderEmployeeCode,
        String assignedSales,
        String assignedSalesEmployeeCode,
        String operator,
        String operatorCode,
        String remark,
        String failedAt
) {
}
