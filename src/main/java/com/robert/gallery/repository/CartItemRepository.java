package com.robert.gallery.repository;

import com.robert.gallery.domain.CartItem;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CartItemRepository implements PanacheRepositoryBase<CartItem, UUID> {

    public List<CartItem> listByUser(UUID userId) {
        return find("user.id = ?1 order by addedAt desc, id asc", userId).list();
    }

    public Optional<CartItem> findByUserAndPainting(UUID userId, UUID paintingId) {
        return find("user.id = ?1 and painting.id = ?2", userId, paintingId).firstResultOptional();
    }

    public void deleteByUserAndPainting(UUID userId, UUID paintingId) {
        delete("user.id = ?1 and painting.id = ?2", userId, paintingId);
    }
}
