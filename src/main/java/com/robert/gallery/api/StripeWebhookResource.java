package com.robert.gallery.api;

import com.robert.gallery.service.CheckoutService;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/payments/stripe/webhook")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class StripeWebhookResource {

    @Inject
    CheckoutService checkout;

    @POST
    public Response receive(String payload, @HeaderParam("Stripe-Signature") String signature) {
        checkout.handleWebhook(payload, signature);
        return Response.noContent().build();
    }
}
