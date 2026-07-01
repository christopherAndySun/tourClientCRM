package com.tourcrm.dto;

public record PerformanceRowResponse(
        String employeeCode,
        String employeeName,
        String role,
        String position,
        String leaderEmployeeCode,
        long totalCount,
        long todayCount,
        long repeatDemandCount,
        long firstDemandCount,
        long dealedCount,
        long refundedCount,
        long landedCount,
        long invalidCount
) {
}
