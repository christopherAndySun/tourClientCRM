package com.tourcrm.dto;

public record DealSaveRequest(
        String customerCode,
        String customerName,
        String deposit,
        String remainingBalance,
        String bookingDate,
        String addWechatDate,
        String quoteText,
        String travelDate,
        String itinerary,
        String dealDate
) {
}
