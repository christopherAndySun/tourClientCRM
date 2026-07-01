package com.tourcrm.dto;

public record UserUpdateRequest(
        String name,
        String password
) {
}
