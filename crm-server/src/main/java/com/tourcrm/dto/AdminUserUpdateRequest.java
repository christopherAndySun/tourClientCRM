package com.tourcrm.dto;

import java.util.List;

public record AdminUserUpdateRequest(
        String name,
        String password,
        String role,
        String position,
        String leaderEmployeeCode,
        String orgType,
        String branchId,
        String branchName,
        List<String> menuPermissions
) {
}
