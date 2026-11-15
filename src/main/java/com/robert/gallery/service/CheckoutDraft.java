package com.robert.gallery.service;

import com.robert.gallery.domain.CheckoutOrderType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CheckoutDraft(
        UUID orderId,
        CheckoutOrderType type,
        UUID auctionId,
        long totalMinor,
        String currency,
        Instant expiresAt,
        List<CheckoutLineItem> items
) {
    public CheckoutDraft {
        items = List.copyOf(items);
    }

    public String successPath() {
        return type == CheckoutOrderType.AUCTION
                ? "/auctions/" + auctionId + "/checkout/success"
                : "/checkout/success";
    }

    public String cancelPath() {
        return type == CheckoutOrderType.AUCTION
                ? "/auctions/" + auctionId
                : "/cart?checkout=cancelled";
    }
}
