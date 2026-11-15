package com.robert.gallery.service;

import java.util.UUID;

public record PendingCheckoutSession(
        UUID orderId,
        String stripeSessionId
) {
}
