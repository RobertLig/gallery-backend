package com.robert.gallery.api;

import com.robert.gallery.dto.AuctionResponse;
import com.robert.gallery.service.AuctionService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.UUID;

@Path("/api/v1/admin/paintings/{paintingId}/auction")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminAuctionResource {

    @Inject
    AuctionService auctionService;

    @POST
    public AuctionResponse start(@PathParam("paintingId") UUID paintingId) {
        return auctionService.start(paintingId);
    }
}
