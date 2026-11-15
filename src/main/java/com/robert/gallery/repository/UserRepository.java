package com.robert.gallery.repository;

import com.robert.gallery.domain.User;
import com.robert.gallery.domain.UserRole;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class UserRepository implements PanacheRepositoryBase<User, UUID> {

    public Optional<User> findByGoogleSubject(String subject) {
        return find("googleSubject", subject).firstResultOptional();
    }

    public Optional<User> findByEmailAndRole(String email, UserRole role) {
        return find("lower(email) = ?1 and role = ?2", email.toLowerCase(java.util.Locale.ROOT), role)
                .firstResultOptional();
    }

    public boolean deleteById(UUID id) {
        return delete("id", id) > 0;
    }
}
