package com.robert.gallery.dto;

import com.robert.gallery.domain.User;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String phone,
        String role,
        String locale
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.id,
                user.email,
                user.displayName,
                user.avatarUrl,
                user.phone,
                user.role.name(),
                user.locale.languageTag()
        );
    }
}
