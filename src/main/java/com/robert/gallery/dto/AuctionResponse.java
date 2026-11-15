package com.robert.gallery.dto;

import com.robert.gallery.domain.Auction;

import java.time.Instant;
import java.util.UUID;

public record AuctionResponse(
        UUID id,
        UUID paintingId,
        String paintingTitle,
        long startingPriceMinor,
        long currentPriceMinor,
        String currency,
        long bidCount,
        Instant startedAt,
        Instant endsAt,
        String status
) {
    public static AuctionResponse from(
            Auction auction,
            long currentPrice,
            long bidCount,
            String currency,
            Instant now
    ) {
        return new AuctionResponse(
                auction.id,
                auction.painting.id,
                auction.painting.title,
                auction.startingPriceMinor,
                currentPrice,
                currency,
                bidCount,
                auction.startedAt,
                auction.endsAt,
                auction.endsAt.isAfter(now) ? "ACTIVE" : "ENDED"
        );
    }
}
