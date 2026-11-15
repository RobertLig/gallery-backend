package com.robert.gallery.repository;

import com.robert.gallery.domain.CheckoutReservation;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@ApplicationScoped
public class CheckoutReservationRepository implements PanacheRepositoryBase<CheckoutReservation, UUID> {

    public boolean isReserved(UUID paintingId) {
        return findByIdOptional(paintingId).isPresent();
    }

    public void deleteByOrder(UUID orderId) {
        delete("order.id", orderId);
    }
}
