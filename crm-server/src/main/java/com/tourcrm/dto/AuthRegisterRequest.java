package com.tourcrm.dto;

import java.util.List;

public record AuthRegisterRequest(
        String name,
        String employeeCode,
        String password,
        String role,
        String position,
        String leaderEmployeeCode,
        List<String> menuPermissions
) {
}
