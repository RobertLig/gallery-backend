package com.robert.gallery.dto;

import com.robert.gallery.domain.PaintingStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PaintingRequest(
        @NotBlank(message = "{error.painting.title.required}")
        @Size(max = 200, message = "{error.painting.title.size}")
        String title,
        @NotBlank(message = "{error.painting.description.required}")
        @Size(max = 10000, message = "{error.painting.description.size}")
        String description,
        @NotBlank(message = "{error.painting.medium.required}")
        @Size(max = 120, message = "{error.painting.medium.size}")
        String medium,
        @NotNull(message = "{error.painting.width.required}")
        @DecimalMin(value = "0.01", message = "{error.painting.dimension.invalid}")
        @DecimalMax(value = "99999.99", message = "{error.painting.dimension.invalid}")
        BigDecimal widthCm,
        @NotNull(message = "{error.painting.height.required}")
        @DecimalMin(value = "0.01", message = "{error.painting.dimension.invalid}")
        @DecimalMax(value = "99999.99", message = "{error.painting.dimension.invalid}")
        BigDecimal heightCm,
        @Min(value = 1000, message = "{error.painting.year.invalid}")
        @Max(value = 9999, message = "{error.painting.year.invalid}")
        Integer artworkYear,
        @NotNull(message = "{error.painting.price.required}")
        @Positive(message = "{error.painting.price.invalid}")
        Long priceMinor,
        @NotNull(message = "{error.painting.status.required}")
        PaintingStatus status
) {
}
