package com.robert.gallery.dto;

import com.robert.gallery.domain.PaintingPhoto;

import java.util.UUID;

public record PaintingPhotoResponse(
        UUID id,
        String url,
        int sortOrder
) {
    public static PaintingPhotoResponse from(PaintingPhoto photo, String url) {
        return new PaintingPhotoResponse(photo.id, url, photo.sortOrder);
    }
}
