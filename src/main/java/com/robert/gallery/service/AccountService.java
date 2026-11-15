package com.robert.gallery.service;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.auth.GoogleIdentity;
import com.robert.gallery.domain.User;
import com.robert.gallery.domain.UserLocale;
import com.robert.gallery.domain.UserRole;
import com.robert.gallery.repository.UserRepository;
import com.robert.gallery.i18n.MessageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class AccountService {

    @Inject
    UserRepository users;

    @Inject
    TokenService tokens;

    @Inject
    MessageService messages;

    @ConfigProperty(name = "app.auth.admin-email")
    Optional<String> configuredAdminEmail;

    @Transactional
    public User signInWithGoogle(GoogleIdentity identity, HttpHeaders headers) {
        User user = users.findByGoogleSubject(identity.subject()).orElseGet(User::new);
        boolean isNewUser = user.googleSubject == null;

        user.googleSubject = identity.subject();
        user.email = identity.email();
        user.displayName = identity.displayName();
        user.avatarUrl = identity.avatarUrl();
        user.locale = UserLocale.from(messages.resolveLocale(headers.getAcceptableLanguages()));
        if (isNewUser) {
            users.persist(user);
        }
        user.role = isAdmin(identity.email()) ? UserRole.ADMIN : UserRole.USER;
        return user;
    }

    public User requireUser(String subject) {
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException exception) {
            throw new DomainException(Response.Status.UNAUTHORIZED, "error.auth.invalid");
        }
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new DomainException(Response.Status.UNAUTHORIZED, "error.auth.invalid"));
    }

    public User consumeRefreshToken(String token) {
        UUID userId = tokens.consumeRefreshToken(token)
                .orElseThrow(() -> new DomainException(
                        Response.Status.UNAUTHORIZED,
                        "error.auth.refresh.invalid"
                ));
        return users.findByIdOptional(userId)
                .orElseThrow(() -> new DomainException(
                        Response.Status.UNAUTHORIZED,
                        "error.auth.refresh.invalid"
                ));
    }

    @Transactional
    public User updatePhone(String subject, String phone) {
        User user = requireUser(subject);
        user.phone = phone;
        return user;
    }

    private boolean isAdmin(String email) {
        String emailToPromote = configuredAdminEmail.map(String::trim).orElse("");
        return !emailToPromote.isBlank()
                && !"not-configured".equalsIgnoreCase(emailToPromote)
                && emailToPromote.equalsIgnoreCase(email);
    }
}
