package com.robert.gallery.dto;

import com.robert.gallery.domain.Painting;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaintingResponse(
        UUID id,
        String title,
        String description,
        String medium,
        BigDecimal widthCm,
        BigDecimal heightCm,
        Integer artworkYear,
        long priceMinor,
        String currency,
        String status,
        List<PaintingPhotoResponse> photos,
        Instant createdAt,
        Instant updatedAt
) {
    public static PaintingResponse from(Painting painting, String currency, List<PaintingPhotoResponse> photos) {
        return new PaintingResponse(
                painting.id,
                painting.title,
                painting.description,
                painting.medium,
                painting.widthCm,
                painting.heightCm,
                painting.artworkYear,
                painting.priceMinor,
                currency,
                painting.status.name(),
                List.copyOf(photos),
                painting.createdAt,
                painting.updatedAt
        );
    }
}
