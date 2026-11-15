package com.robert.gallery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class Auction {

    @Id
    public UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "painting_id", nullable = false)
    public Painting painting;

    @Column(name = "starting_price_minor", nullable = false)
    public long startingPriceMinor;

    @Column(name = "ends_at", nullable = false)
    public Instant endsAt;

    @Column(name = "started_at", nullable = false, updatable = false)
    public Instant startedAt;
}
