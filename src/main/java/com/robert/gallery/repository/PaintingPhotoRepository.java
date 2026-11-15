package com.robert.gallery.repository;

import com.robert.gallery.domain.PaintingPhoto;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PaintingPhotoRepository implements PanacheRepositoryBase<PaintingPhoto, UUID> {

    public List<PaintingPhoto> findByPaintingId(UUID paintingId) {
        return find("painting.id = ?1 order by sortOrder", paintingId).list();
    }

    public long countByPaintingId(UUID paintingId) {
        return count("painting.id", paintingId);
    }
}
