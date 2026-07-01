package com.tourcrm.dto;

public record DealResponse(
        String dealCode,
        String customerCode,
        String customerName,
        String deposit,
        String bookingDate,
        String addWechatDate,
        String quoteText,
        String travelDate,
        String itinerary,
        String dealDate,
        String dealUser,
        String dealUserCode,
        Integer totalDealSequence,
        Integer personalDealSequence,
        String status,
        String refundAmount,
        String refundRemark,
        String refundedAt,
        String landingAt,
        String landingRemark,
        String createdAt,
        String updatedAt
) {
}
