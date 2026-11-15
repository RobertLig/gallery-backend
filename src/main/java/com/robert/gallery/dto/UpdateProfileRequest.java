package com.robert.gallery.dto;

import jakarta.validation.constraints.Pattern;

public record UpdateProfileRequest(
        @Pattern(regexp = "^\\+[1-9][0-9]{1,14}$", message = "{error.user.phone.invalid}")
        String phone
) {
}
