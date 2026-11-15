package com.robert.gallery.repository;

import com.robert.gallery.domain.Auction;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AuctionRepository implements PanacheRepositoryBase<Auction, UUID> {

    public Optional<Auction> findByIdForUpdate(UUID id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResultOptional();
    }

    public Optional<Auction> findActiveForPainting(UUID paintingId, Instant now) {
        return find("painting.id = ?1 and endsAt > ?2 order by startedAt desc", paintingId, now)
                .firstResultOptional();
    }

    public Optional<Auction> findLatestForPainting(UUID paintingId) {
        return find("painting.id = ?1 order by startedAt desc", paintingId).firstResultOptional();
    }

    public List<Auction> listActive(Instant now, int page, int size) {
        return find("endsAt > ?1 order by endsAt asc, id asc", now)
                .page(page, size)
                .list();
    }

    public long countActive(Instant now) {
        return count("endsAt > ?1", now);
    }
}
