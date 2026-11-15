package com.robert.gallery.dto;

import java.util.UUID;

public record CheckoutOrderItemResponse(
        UUID paintingId,
        String paintingTitle,
        long amountMinor
) {
}
