package com.robert.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record PhotoUploadRequest(
        @NotBlank(message = "{error.photo.content-type.required}")
        String contentType,
        @Positive(message = "{error.photo.size.invalid}")
        long fileSize,
        @Size(max = 255, message = "{error.photo.filename.invalid}")
        String filename
) {
}
