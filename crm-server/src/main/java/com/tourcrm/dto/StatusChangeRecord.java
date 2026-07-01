package com.tourcrm.dto;

public record StatusChangeRecord(
        String status,
        String statusText,
        String operator,
        String operatorCode,
        String depositAmount,
        String remark,
        String createdAt
) {
}
