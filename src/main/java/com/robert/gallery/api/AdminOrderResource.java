package com.robert.gallery.api;

import com.robert.gallery.dto.AdminOrderResponse;
import com.robert.gallery.dto.PageResponse;
import com.robert.gallery.service.CheckoutOrderService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/admin/orders")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminOrderResource {

    @Inject
    CheckoutOrderService orders;

    @GET
    public PageResponse<AdminOrderResponse> list(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        return orders.getAdminOrders(page == null ? 0 : page, size == null ? 20 : size);
    }
}
