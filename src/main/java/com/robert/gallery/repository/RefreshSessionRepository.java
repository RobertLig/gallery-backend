package com.robert.gallery.repository;

import com.robert.gallery.domain.RefreshSession;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RefreshSessionRepository implements PanacheRepositoryBase<RefreshSession, UUID> {

    public Optional<RefreshSession> findByTokenHashForUpdate(String tokenHash) {
        return find("tokenHash", tokenHash)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();
    }
}
