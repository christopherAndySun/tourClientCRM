package com.tourcrm.dto;

import java.util.List;

public record OcrRecognizeResponse(
        List<String> candidates,
        String fullText,
        String message
) {
}
