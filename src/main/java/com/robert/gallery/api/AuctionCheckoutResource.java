package com.robert.gallery.api;

import com.robert.gallery.dto.CheckoutResponse;
import com.robert.gallery.service.CheckoutService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;

@Path("/api/v1/auctions")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class AuctionCheckoutResource {

    @Inject
    CheckoutService checkout;

    @Inject
    JsonWebToken principal;

    @POST
    @Path("/{id}/checkout")
    public CheckoutResponse startCheckout(@PathParam("id") UUID auctionId) {
        return checkout.startAuctionCheckout(principal.getSubject(), auctionId);
    }
}
