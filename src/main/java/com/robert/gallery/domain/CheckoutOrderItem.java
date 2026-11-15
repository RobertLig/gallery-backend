package com.robert.gallery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "checkout_order_items")
public class CheckoutOrderItem {

    @Id
    public UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    public CheckoutOrder order;

    @Column(name = "painting_id", nullable = false)
    public UUID paintingId;

    @Column(name = "painting_title", nullable = false, length = 200)
    public String paintingTitle;

    @Column(name = "amount_minor", nullable = false)
    public long amountMinor;
}
