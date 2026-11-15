package com.robert.gallery.service;

import com.robert.gallery.domain.RefreshSession;
import com.robert.gallery.domain.User;
import com.robert.gallery.repository.RefreshSessionRepository;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class TokenService {

    public static final long ACCESS_TOKEN_SECONDS = 900;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Inject
    RefreshSessionRepository refreshSessions;

    @ConfigProperty(name = "app.auth.refresh-days")
    int refreshDays;

    public String issueAccessToken(User user) {
        return Jwt.issuer("gallery-app")
                .subject(user.id.toString())
                .upn(user.email)
                .groups(user.role.name())
                .claim("locale", user.locale.languageTag())
                .expiresAt(Instant.now().plusSeconds(ACCESS_TOKEN_SECONDS))
                .sign();
    }

    public static int refreshTokenLifetimeSeconds(int days) {
        if (days < 1 || days > 365) {
            throw new IllegalStateException("Refresh token lifetime must be between 1 and 365 days");
        }
        return Math.toIntExact(Duration.ofDays(days).getSeconds());
    }

    @Transactional
    public String createRefreshToken(User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshSession session = new RefreshSession();
        session.user = user;
        session.tokenHash = hash(token);
        session.expiresAt = Instant.now().plusSeconds(refreshTokenLifetimeSeconds(refreshDays));
        refreshSessions.persist(session);
        return token;
    }

    @Transactional
    public Optional<UUID> consumeRefreshToken(String token) {
        Optional<RefreshSession> found = refreshSessions.findByTokenHashForUpdate(hash(token));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshSession session = found.get();
        if (!session.expiresAt.isAfter(Instant.now())) {
            refreshSessions.delete(session);
            return Optional.empty();
        }
        UUID userId = session.user.id;
        refreshSessions.delete(session);
        return Optional.of(userId);
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        refreshSessions.findByTokenHashForUpdate(hash(token)).ifPresent(refreshSessions::delete);
    }

    private String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}
