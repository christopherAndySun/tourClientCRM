package com.tourcrm.dto;

public record EmployeeCluesResponse(
        PerformanceRowResponse performance,
        PageResponse<ClueResponse> clues
) {
}
