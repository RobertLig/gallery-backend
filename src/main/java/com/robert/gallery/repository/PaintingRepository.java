package com.robert.gallery.repository;

import com.robert.gallery.domain.Painting;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class PaintingRepository implements PanacheRepositoryBase<Painting, UUID> {
}
