package com.tourcrm.dto;

public record ClueStatusUpdateRequest(
        String status,
        String depositAmount,
        String remark,
        String refundAmount,
        String refundedAt,
        String landingAt,
        String landingRemark
) {
}
