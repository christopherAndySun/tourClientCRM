package com.tourcrm.dto;

import java.util.List;

public record ClueResponse(
        String customerCode,
        String sourcePlatform,
        String contactInfo,
        Boolean hasWechatId,
        String uploader,
        String uploaderEmployeeCode,
        String status,
        String remark,
        List<ImageFileDto> douyinImages,
        List<ImageFileDto> wechatImages,
        Boolean repeatDemand,
        String originalCustomerCode,
        Integer demandSequence,
        String assignedSales,
        String assignedSalesEmployeeCode,
        String depositAmount,
        String statusRemark,
        String refundAmount,
        String refundedAt,
        String landingAt,
        String landingRemark,
        List<StatusChangeRecord> statusHistory,
        List<FollowRecord> followRecords,
        List<AssignLogRecord> assignLogs,
        List<OperationLogRecord> operationLogs,
        String createdAt,
        String updatedAt
) {
}
