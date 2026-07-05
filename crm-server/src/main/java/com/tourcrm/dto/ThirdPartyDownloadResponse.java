package com.tourcrm.dto;

public record ThirdPartyDownloadResponse(
        ClueResponse clue,
        String downloadedBy,
        String downloadedByCode,
        String downloadedAt
) {
}
