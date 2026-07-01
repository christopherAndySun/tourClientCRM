package com.tourcrm.dto;

public record MenuItemRecord(
        String code,
        String groupCode,
        String groupName,
        String name,
        String description,
        String path,
        Integer sort,
        Boolean enabled
) {
}
