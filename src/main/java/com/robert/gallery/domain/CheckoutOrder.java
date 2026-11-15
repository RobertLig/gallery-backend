package com.robert.gallery.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "checkout_orders")
public class CheckoutOrder {

    @Id
    public UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    public User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    public CheckoutOrderStatus status = CheckoutOrderStatus.PENDING_PAYMENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public CheckoutOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_mode", nullable = false, length = 16)
    public FulfillmentMode fulfillmentMode = FulfillmentMode.PICKUP;

    @Column(name = "auction_id")
    public UUID auctionId;

    @Column(name = "total_minor", nullable = false)
    public long totalMinor;

    @Column(nullable = false, length = 3)
    public String currency;

    @Column(name = "stripe_session_id", unique = true, length = 255)
    public String stripeSessionId;

    @Column(name = "expires_at", nullable = false)
    public Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    public List<CheckoutOrderItem> items = new ArrayList<>();
}
