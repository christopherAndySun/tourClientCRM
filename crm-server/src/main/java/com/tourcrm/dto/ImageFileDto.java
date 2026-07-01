package com.tourcrm.dto;

public record ImageFileDto(
        String name,
        String url,
        String uid,
        Integer sortOrder
) {
}
