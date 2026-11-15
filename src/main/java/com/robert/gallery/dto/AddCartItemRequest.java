package com.robert.gallery.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddCartItemRequest(
        @NotNull(message = "{error.cart.painting.required}") UUID paintingId
) {
}
