package com.robert.gallery.dto;

import java.time.Instant;
import java.util.UUID;

public record CheckoutOrderResponse(
        UUID id,
        String status,
        long totalMinor,
        String currency,
        String fulfillmentMode,
        Instant expiresAt
) {
}
