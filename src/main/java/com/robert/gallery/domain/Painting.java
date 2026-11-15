package com.robert.gallery.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "paintings")
public class Painting {

    @Id
    public UUID id = UUID.randomUUID();

    @Column(nullable = false, length = 200)
    public String title;

    @Column(nullable = false, length = 10000)
    public String description;

    @Column(nullable = false, length = 120)
    public String medium;

    @Column(name = "width_cm", nullable = false, precision = 7, scale = 2)
    public BigDecimal widthCm;

    @Column(name = "height_cm", nullable = false, precision = 7, scale = 2)
    public BigDecimal heightCm;

    @Column(name = "artwork_year")
    public Integer artworkYear;

    @Column(name = "price_minor", nullable = false)
    public Long priceMinor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public PaintingStatus status = PaintingStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @OneToMany(mappedBy = "painting", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("sortOrder ASC")
    public List<PaintingPhoto> photos = new ArrayList<>();
}
