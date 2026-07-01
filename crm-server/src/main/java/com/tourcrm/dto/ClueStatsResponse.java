package com.tourcrm.dto;

import java.util.Map;

public record ClueStatsResponse(
        long totalCount,
        long todayCount,
        long repeatDemandCount,
        long firstDemandCount,
        Map<String, Long> statusCounts,
        Map<String, Long> uploaderCounts,
        Map<String, Long> salesCounts
) {
}
