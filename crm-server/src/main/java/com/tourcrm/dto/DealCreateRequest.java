package com.tourcrm.dto;

import java.time.LocalDate;

public record DealCreateRequest(
        Long customerClueId,
        Long salesUserId,
        String customerName,
        String deposit,
        LocalDate bookingDate,
        LocalDate addWechatDate,
        String quoteText,
        String travelDate,
        String itinerary,
        LocalDate dealDate
) {
}

