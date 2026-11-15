package com.robert.gallery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "painting_photos", uniqueConstraints = {
        @UniqueConstraint(name = "uk_painting_photos_object_key", columnNames = "object_key"),
        @UniqueConstraint(name = "uk_painting_photos_order", columnNames = {"painting_id", "sort_order"})
})
public class PaintingPhoto {

    @Id
    public UUID id = UUID.randomUUID();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "painting_id", nullable = false)
    public Painting painting;

    @Column(name = "object_key", nullable = false, length = 512)
    public String objectKey;

    @Column(name = "content_type", nullable = false, length = 64)
    public String contentType;

    @Column(name = "file_size", nullable = false)
    public long fileSize;

    @Column(name = "sort_order", nullable = false)
    public int sortOrder;

    @Column(nullable = false)
    public boolean uploaded;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;
}
