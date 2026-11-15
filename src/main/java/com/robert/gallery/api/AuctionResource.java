package com.robert.gallery.api;

import com.robert.gallery.dto.AuctionResponse;
import com.robert.gallery.dto.BidResponse;
import com.robert.gallery.dto.PageResponse;
import com.robert.gallery.dto.PlaceBidRequest;
import com.robert.gallery.service.AuctionService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.util.List;
import java.util.UUID;

@Path("/api/v1/auctions")
@Produces(MediaType.APPLICATION_JSON)
public class AuctionResource {

    @Inject
    AuctionService auctionService;

    @Inject
    JsonWebToken principal;

    @GET
    public PageResponse<AuctionResponse> list(
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        return auctionService.listActive(page == null ? 0 : page, size == null ? 20 : size);
    }

    @GET
    @Path("/{id}")
    public AuctionResponse get(@PathParam("id") UUID id) {
        return auctionService.get(id);
    }

    @GET
    @Path("/{id}/bids")
    public List<BidResponse> recentBids(@PathParam("id") UUID id) {
        return auctionService.recentBids(id);
    }

    @POST
    @Path("/{id}/bids")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authenticated
    public AuctionResponse placeBid(
            @PathParam("id") UUID id,
            @NotNull(message = "{error.request.required}") @Valid PlaceBidRequest request
    ) {
        return auctionService.placeBid(id, principal.getSubject(), request);
    }
}
