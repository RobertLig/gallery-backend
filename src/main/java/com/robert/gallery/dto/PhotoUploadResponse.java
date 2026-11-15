package com.robert.gallery.dto;

import java.util.Map;
import java.util.UUID;

public record PhotoUploadResponse(
        UUID id,
        String objectKey,
        String uploadUrl,
        int expiresInSeconds,
        Map<String, String> headers
) {
    public PhotoUploadResponse {
        headers = Map.copyOf(headers);
    }
}
