package com.tourcrm.dto;

import java.util.List;

public record UserSession(
        String name,
        String employeeCode,
        String role,
        String position,
        String leaderEmployeeCode,
        String orgType,
        String branchId,
        String branchName,
        List<String> menuPermissions
) {
}
