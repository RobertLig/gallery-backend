package com.robert.gallery.api;

import com.robert.gallery.domain.User;
import com.robert.gallery.dto.UpdateProfileRequest;
import com.robert.gallery.dto.UserResponse;
import com.robert.gallery.service.AccountService;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import io.quarkus.security.Authenticated;
import org.eclipse.microprofile.jwt.JsonWebToken;

@Path("/api/v1/users/me")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    @Inject
    JsonWebToken principal;

    @Inject
    AccountService accounts;

    @GET
    public UserResponse getProfile() {
        return UserResponse.from(accounts.requireUser(principal.getSubject()));
    }

    @PATCH
    public UserResponse updateProfile(
            @NotNull(message = "{error.request.required}") @Valid UpdateProfileRequest request
    ) {
        User user = accounts.updatePhone(principal.getSubject(), request.phone());
        return UserResponse.from(user);
    }
}
