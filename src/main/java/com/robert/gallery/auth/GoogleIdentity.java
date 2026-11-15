package com.robert.gallery.auth;

public record GoogleIdentity(
        String subject,
        String email,
        String displayName,
        String avatarUrl
) {
}
