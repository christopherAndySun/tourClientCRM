package com.tourcrm.dto;

import java.util.List;

public record EmployeeCluesResponse(
        PerformanceRowResponse performance,
        List<ClueResponse> clues
) {
}
