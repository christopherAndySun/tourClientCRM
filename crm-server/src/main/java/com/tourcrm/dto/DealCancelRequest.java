package com.tourcrm.dto;

public record DealCancelRequest(
        String remark,
        String refundAmount,
        String refundedAt
) {
}
