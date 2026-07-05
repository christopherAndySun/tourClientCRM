package com.tourcrm.dto;

import java.util.List;

public record RealtimeEvent(
        String type,
        String customerCode,
        String title,
        String message,
        List<String> targets,
        String createdAt
) {
}
