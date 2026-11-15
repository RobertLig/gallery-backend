package com.robert.gallery.service;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.domain.UserRole;
import com.robert.gallery.dto.GalleryContactResponse;
import com.robert.gallery.repository.UserRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;

@ApplicationScoped
public class GalleryContactService {

    @Inject
    UserRepository users;

    @ConfigProperty(name = "app.auth.admin-email")
    Optional<String> adminEmail;

    @Transactional
    public GalleryContactResponse getAdminContact() {
        String email = adminEmail.map(String::trim).orElse("");
        if (email.isBlank() || "not-configured".equalsIgnoreCase(email)) {
            throw unavailable();
        }

        return users.findByEmailAndRole(email, UserRole.ADMIN)
                .filter(user -> user.phone != null && !user.phone.isBlank())
                .map(user -> new GalleryContactResponse(user.phone, true))
                .orElseThrow(this::unavailable);
    }

    private DomainException unavailable() {
        return new DomainException(Response.Status.SERVICE_UNAVAILABLE, "error.contact.unavailable");
    }
}
