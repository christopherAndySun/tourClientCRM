package com.tourcrm.dto;

import java.util.List;

public record CustomerHistoryResponse(
        String customerKey,
        String rootCustomerCode,
        String primaryContactInfo,
        Integer totalDemands,
        List<ClueResponse> demands
) {
}
