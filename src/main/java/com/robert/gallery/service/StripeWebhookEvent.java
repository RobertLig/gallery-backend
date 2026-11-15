package com.robert.gallery.service;

import java.util.UUID;

public record StripeWebhookEvent(
        String eventId,
        String type,
        UUID orderId,
        String sessionId,
        String paymentStatus,
        Long amountTotal,
        String currency
) {
}
