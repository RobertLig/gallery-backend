package com.robert.gallery.dto;

import com.robert.gallery.domain.CheckoutOrder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderHistoryResponse(
        UUID id,
        String status,
        String type,
        UUID auctionId,
        long totalMinor,
        String currency,
        String fulfillmentMode,
        Instant createdAt,
        Instant expiresAt,
        List<CheckoutOrderItemResponse> items
) {
    public OrderHistoryResponse {
        items = List.copyOf(items);
    }

    public static OrderHistoryResponse from(CheckoutOrder order) {
        return new OrderHistoryResponse(
                order.id,
                order.status.name(),
                order.type.name(),
                order.auctionId,
                order.totalMinor,
                order.currency,
                order.fulfillmentMode.name(),
                order.createdAt,
                order.expiresAt,
                order.items.stream()
                        .map(item -> new CheckoutOrderItemResponse(
                                item.paintingId,
                                item.paintingTitle,
                                item.amountMinor
                        ))
                        .toList()
        );
    }
}
