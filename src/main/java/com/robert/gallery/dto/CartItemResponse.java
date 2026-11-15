package com.robert.gallery.dto;

import java.time.Instant;
import java.util.UUID;

public record CartItemResponse(
        UUID paintingId,
        Instant addedAt,
        boolean purchasable,
        PaintingResponse painting
) {
}
