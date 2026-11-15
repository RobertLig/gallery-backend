package com.robert.gallery.api;

import com.robert.gallery.dto.CheckoutOrderResponse;
import com.robert.gallery.dto.CheckoutResponse;
import com.robert.gallery.dto.CollectionDetailsResponse;
import com.robert.gallery.service.CheckoutOrderService;
import com.robert.gallery.service.CheckoutService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.UUID;
import com.robert.gallery.dto.OrderHistoryResponse;
import com.robert.gallery.dto.PageResponse;

@Path("/api/v1/checkout")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class CheckoutResource {

    @Inject
    CheckoutService checkout;

    @Inject
    CheckoutOrderService orders;

    @Inject
    JsonWebToken principal;

    @POST
    @Path("/cart")
    public CheckoutResponse startCartCheckout() {
        return checkout.startCartCheckout(principal.getSubject());
    }

    @GET
    @Path("/orders")
    public PageResponse<OrderHistoryResponse> getOrderHistory(
            @jakarta.ws.rs.QueryParam("page") Integer page,
            @jakarta.ws.rs.QueryParam("size") Integer size
    ) {
        return orders.getOrderHistory(
                principal.getSubject(),
                page == null ? 0 : page,
                size == null ? 20 : size
        );
    }

    @GET
    @Path("/orders/{id}")
    public CheckoutOrderResponse getOrder(@PathParam("id") UUID id) {
        return orders.getOrder(principal.getSubject(), id);
    }

    @GET
    @Path("/orders/pending")
    public java.util.List<CheckoutOrderResponse> getPendingOrders() {
        return orders.getPendingOrders(principal.getSubject());
    }

    @POST
    @Path("/orders/{id}/session")
    public CheckoutResponse retryCheckout(@PathParam("id") UUID id) {
        return checkout.retryCheckout(principal.getSubject(), id);
    }

    @GET
    @Path("/orders/{id}/collection-details")
    public CollectionDetailsResponse getCollectionDetails(@PathParam("id") UUID id) {
        return orders.getCollectionDetails(principal.getSubject(), id);
    }
}
