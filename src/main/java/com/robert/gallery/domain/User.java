package com.robert.gallery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_google_subject", columnNames = "google_subject"),
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {

    @Id
    public UUID id = UUID.randomUUID();

    @Column(name = "google_subject", nullable = false, length = 255)
    public String googleSubject;

    @Column(nullable = false, length = 320)
    public String email;

    @Column(name = "display_name", nullable = false, length = 200)
    public String displayName;

    @Column(name = "avatar_url", length = 2048)
    public String avatarUrl;

    @Column(length = 16)
    public String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 2)
    public UserLocale locale = UserLocale.EN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;
}
