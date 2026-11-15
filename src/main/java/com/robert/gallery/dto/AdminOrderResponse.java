package com.robert.gallery.dto;

import java.util.UUID;

public record AdminOrderResponse(
        OrderHistoryResponse order,
        UUID buyerId,
        String buyerName,
        String buyerEmail,
        String buyerPhone
) {
}
