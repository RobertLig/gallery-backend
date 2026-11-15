package com.robert.gallery.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record PhotoOrderRequest(
        @NotEmpty(message = "{error.photo.order.invalid}")
        @Size(max = 10, message = "{error.photo.order.invalid}")
        List<UUID> photoIds
) {
}
