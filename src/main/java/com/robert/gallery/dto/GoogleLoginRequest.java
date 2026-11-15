package com.robert.gallery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GoogleLoginRequest(
        @NotBlank(message = "{error.auth.credential.required}")
        @Size(max = 8192, message = "{error.auth.google.invalid}")
        String credential
) {
}
