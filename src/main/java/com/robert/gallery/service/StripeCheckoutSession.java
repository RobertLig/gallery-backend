package com.robert.gallery.service;

import java.time.Instant;

public record StripeCheckoutSession(
        String id,
        String url,
        Instant expiresAt
) {
}
