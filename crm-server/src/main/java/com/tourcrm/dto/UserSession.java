package com.tourcrm.dto;

import java.util.List;

public record UserSession(
        String name,
        String employeeCode,
        String role,
        String position,
        String leaderEmployeeCode,
        List<String> menuPermissions
) {
}
