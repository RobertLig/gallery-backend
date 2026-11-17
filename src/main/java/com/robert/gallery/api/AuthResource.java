package com.robert.gallery.api;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.auth.GoogleTokenVerifier;
import com.robert.gallery.domain.User;
import com.robert.gallery.dto.GoogleLoginRequest;
import com.robert.gallery.dto.TokenResponse;
import com.robert.gallery.dto.UserResponse;
import com.robert.gallery.service.AccountService;
import com.robert.gallery.service.TokenService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.CookieParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.NewCookie;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.annotation.security.PermitAll;

@Path("/api/v1/auth")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class AuthResource {

    private static final String REFRESH_COOKIE = "gallery_refresh";

    @Inject
    AccountService accounts;

    @Inject
    TokenService tokens;

    @Inject
    GoogleTokenVerifier googleTokenVerifier;

    @ConfigProperty(name = "app.auth.refresh-days")
    int refreshDays;

    @ConfigProperty(name = "app.auth.cookie.secure")
    boolean secureCookie;

    @ConfigProperty(name = "app.auth.cookie.same-site")
    String sameSite;

    @POST
    @Path("/google")
    public Response googleLogin(
            @NotNull(message = "{error.request.required}") @Valid GoogleLoginRequest request,
            @Context HttpHeaders headers
    ) {
        User user = accounts.signInWithGoogle(googleTokenVerifier.verify(request.credential()), headers);
        return tokenResponse(user, tokens.createRefreshToken(user), Response.Status.OK);
    }

    @POST
    @Path("/refresh")
    public Response refresh(@CookieParam(REFRESH_COOKIE) String refreshToken) {
        if (!isValidRefreshToken(refreshToken)) {
            throw new DomainException(Response.Status.UNAUTHORIZED, "error.auth.refresh.invalid");
        }
        User user = accounts.consumeRefreshToken(refreshToken);
        return tokenResponse(user, tokens.createRefreshToken(user), Response.Status.OK);
    }

    @POST
    @Path("/logout")
    public Response logout(@CookieParam(REFRESH_COOKIE) String refreshToken) {
        if (isValidRefreshToken(refreshToken)) {
            tokens.revokeRefreshToken(refreshToken);
        }
        return Response.noContent().cookie(refreshCookie("", 0)).build();
    }

    private Response tokenResponse(User user, String refreshToken, Response.Status status) {
        return Response.status(status)
                .entity(new TokenResponse(
                        tokens.issueAccessToken(user),
                        TokenService.ACCESS_TOKEN_SECONDS,
                        UserResponse.from(user)
                ))
                .cookie(refreshCookie(refreshToken, TokenService.refreshTokenLifetimeSeconds(refreshDays)))
                .build();
    }

    private boolean isValidRefreshToken(String token) {
        return token != null && token.length() == 43;
    }

    private NewCookie refreshCookie(String value, int maxAge) {
        return new NewCookie.Builder(REFRESH_COOKIE)
                .value(value)
                .path("/api/v1/auth")
                .httpOnly(true)
                .secure(secureCookie)
                .sameSite(NewCookie.SameSite.valueOf(sameSite.toUpperCase(java.util.Locale.ROOT)))
                .maxAge(maxAge)
                .build();
    }
}
