package com.robert.gallery.api;

import com.robert.gallery.dto.AddCartItemRequest;
import com.robert.gallery.dto.CartResponse;
import com.robert.gallery.service.CartService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/api/v1/cart")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class CartResource {

    @Inject
    CartService carts;

    @Inject
    JsonWebToken principal;

    @GET
    public CartResponse get() {
        return carts.get(principal.getSubject());
    }

    @POST
    @Path("/items")
    @Consumes(MediaType.APPLICATION_JSON)
    public CartResponse add(
            @NotNull(message = "{error.request.required}") @Valid AddCartItemRequest request
    ) {
        return carts.add(principal.getSubject(), request.paintingId());
    }

    @DELETE
    @Path("/items/{paintingId}")
    public Response remove(@PathParam("paintingId") UUID paintingId) {
        carts.remove(principal.getSubject(), paintingId);
        return Response.noContent().build();
    }
}
