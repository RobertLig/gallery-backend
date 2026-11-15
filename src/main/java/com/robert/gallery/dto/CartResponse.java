package com.robert.gallery.dto;

import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        long totalMinor,
        String currency
) {
    public CartResponse {
        items = List.copyOf(items);
    }
}
