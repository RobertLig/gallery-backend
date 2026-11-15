package com.robert.gallery.dto;

import jakarta.validation.constraints.Positive;

public record PlaceBidRequest(
        @Positive(message = "{error.auction.bid.invalid}")
        long amountMinor
) {
}
