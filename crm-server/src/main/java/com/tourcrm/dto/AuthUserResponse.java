package com.tourcrm.dto;

import java.util.List;

public record AuthUserResponse(
        String token,
        String name,
        String employeeCode,
        String role,
        String position,
        String leaderEmployeeCode,
        String orgType,
        String branchId,
        String branchName,
        List<String> menuPermissions,
        String expiresAt
) {
}
