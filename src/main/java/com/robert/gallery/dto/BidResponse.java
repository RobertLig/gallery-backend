package com.robert.gallery.dto;

import com.robert.gallery.domain.Bid;

import java.time.Instant;
import java.util.UUID;

public record BidResponse(
        UUID id,
        long amountMinor,
        Instant placedAt
) {
    public static BidResponse from(Bid bid) {
        return new BidResponse(bid.id, bid.amountMinor, bid.placedAt);
    }
}
