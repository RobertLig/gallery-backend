package com.robert.gallery.repository;

import com.robert.gallery.domain.CheckoutOrder;
import com.robert.gallery.domain.CheckoutOrderStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CheckoutOrderRepository implements PanacheRepositoryBase<CheckoutOrder, UUID> {

    public Optional<CheckoutOrder> findByIdForUpdate(UUID id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public Optional<CheckoutOrder> findByStripeSessionId(String sessionId) {
        return find("stripeSessionId", sessionId).firstResultOptional();
    }

    public java.util.List<CheckoutOrder> listPendingExpired(Instant cutoff, int limit) {
        return find("status in (?1, ?2) and expiresAt <= ?3 order by expiresAt asc",
                CheckoutOrderStatus.PENDING_PAYMENT, CheckoutOrderStatus.ASYNC_PAYMENT_PENDING, cutoff)
                .page(0, limit)
                .list();
    }

    public java.util.List<CheckoutOrder> listPendingByUser(UUID userId) {
        return find("user.id = ?1 and status in (?2, ?3) order by createdAt desc",
                userId, CheckoutOrderStatus.PENDING_PAYMENT, CheckoutOrderStatus.ASYNC_PAYMENT_PENDING)
                .list();
    }

    public java.util.List<CheckoutOrder> listByUser(UUID userId, int page, int size) {
        return find("user.id = ?1 order by createdAt desc, id desc", userId)
                .page(page, size)
                .list();
    }

    public long countByUser(UUID userId) {
        return count("user.id", userId);
    }

    public boolean hasPendingByUser(UUID userId) {
        return count("user.id = ?1 and status in (?2, ?3)",
                userId,
                CheckoutOrderStatus.PENDING_PAYMENT,
                CheckoutOrderStatus.ASYNC_PAYMENT_PENDING) > 0;
    }

    public java.util.List<CheckoutOrder> listAll(int page, int size) {
        return find("order by createdAt desc, id desc")
                .page(page, size)
                .list();
    }
}
