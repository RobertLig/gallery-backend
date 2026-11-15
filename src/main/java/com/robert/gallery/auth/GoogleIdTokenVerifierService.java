package com.robert.gallery.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.robert.gallery.api.error.DomainException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class GoogleIdTokenVerifierService implements GoogleTokenVerifier {

    private static final Logger LOG = Logger.getLogger(GoogleIdTokenVerifierService.class);

    @ConfigProperty(name = "google.client.id")
    Optional<String> configuredClientId;

    private GoogleIdTokenVerifier verifier;

    @PostConstruct
    void initialize() {
        String clientId = configuredClientId.filter(value -> !value.isBlank()).orElse("");
        if (!clientId.isBlank() && !clientId.equals("replace-with-google-client-id")) {
            verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(),
                    GsonFactory.getDefaultInstance()
            ).setAudience(List.of(clientId)).build();
        }
    }

    @Override
    public GoogleIdentity verify(String credential) {
        if (verifier == null) {
            throw new DomainException(Response.Status.SERVICE_UNAVAILABLE, "error.auth.google.unconfigured");
        }

        GoogleIdToken token;
        try {
            token = verifier.verify(credential);
        } catch (GeneralSecurityException | IOException exception) {
            LOG.warn("Could not verify Google ID token", exception);
            throw new DomainException(Response.Status.SERVICE_UNAVAILABLE, "error.auth.google.unavailable");
        }

        if (token == null) {
            throw new DomainException(Response.Status.UNAUTHORIZED, "error.auth.google.invalid");
        }

        GoogleIdToken.Payload payload = token.getPayload();
        String name = payload.get("name") == null ? null : payload.get("name").toString();
        String picture = payload.get("picture") == null ? null : payload.get("picture").toString();
        if (!Boolean.TRUE.equals(payload.getEmailVerified())
                || payload.getSubject() == null
                || payload.getEmail() == null
                || payload.getEmail().length() > 320
                || name == null
                || name.isBlank()
                || name.length() > 200
                || !isValidPictureUrl(picture)) {
            throw new DomainException(Response.Status.UNAUTHORIZED, "error.auth.google.invalid");
        }

        return new GoogleIdentity(
                payload.getSubject(),
                payload.getEmail().toLowerCase(java.util.Locale.ROOT),
                name,
                picture
        );
    }

    private boolean isValidPictureUrl(String picture) {
        if (picture == null) {
            return true;
        }
        try {
            URI uri = URI.create(picture);
            return picture.length() <= 2048 && "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
