package com.tourcrm.dto;

import java.util.List;

public record CustomerHistoryResponse(
        String customerKey,
        Integer totalDemands,
        List<ClueResponse> demands
) {
}
