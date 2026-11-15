package com.robert.gallery.repository;

import com.robert.gallery.domain.Bid;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class BidRepository implements PanacheRepositoryBase<Bid, UUID> {

    public long countByAuction(UUID auctionId) {
        return count("auction.id", auctionId);
    }

    public long currentPrice(UUID auctionId, long startingPrice) {
        Long highestBid = find("select max(amountMinor) from Bid where auction.id = ?1", auctionId)
                .project(Long.class)
                .firstResult();
        return highestBid == null ? startingPrice : highestBid;
    }

    public Optional<Bid> highestForAuction(UUID auctionId) {
        return find("auction.id = ?1 order by amountMinor desc, placedAt asc", auctionId)
                .firstResultOptional();
    }

    public boolean hasBidWithinUserCommitmentWindow(UUID userId, Instant now) {
        return count("bidder.id = ?1 and auction.endsAt > ?2", userId, now.minus(java.time.Duration.ofHours(24))) > 0;
    }

    public List<Bid> recentByAuction(UUID auctionId, int limit) {
        return find("auction.id = ?1 order by placedAt desc", auctionId)
                .page(0, limit)
                .list();
    }
}
