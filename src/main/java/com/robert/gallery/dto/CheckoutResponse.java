package com.robert.gallery.dto;

import java.time.Instant;
import java.util.UUID;

public record CheckoutResponse(
        UUID orderId,
        String checkoutUrl,
        Instant expiresAt
) {
}
