package com.tourcrm.dto;

public record OcrRecognizeRequest(
        String imageBase64,
        String imageUrl
) {
}
