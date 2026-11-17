package com.robert.gallery.api;

import com.robert.gallery.dto.PageResponse;
import com.robert.gallery.dto.PaintingResponse;
import com.robert.gallery.service.PaintingService;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.annotation.security.PermitAll;

import java.util.UUID;

@Path("/api/v1/paintings")
@Produces(MediaType.APPLICATION_JSON)
@PermitAll
public class PaintingResource {

    @Inject
    PaintingService paintingService;

    @GET
    public PageResponse<PaintingResponse> list(
            @QueryParam("q") String search,
            @QueryParam("medium") String medium,
            @QueryParam("minPrice") Long minPrice,
            @QueryParam("maxPrice") Long maxPrice,
            @QueryParam("minYear") Integer minYear,
            @QueryParam("maxYear") Integer maxYear,
            @QueryParam("status") String status,
            @QueryParam("sort") String sort,
            @QueryParam("page") Integer page,
            @QueryParam("size") Integer size
    ) {
        return paintingService.list(
                false, search, medium, minPrice, maxPrice, minYear, maxYear,
                status, sort, page == null ? 0 : page, size == null ? 20 : size
        );
    }

    @GET
    @Path("/{id}")
    public PaintingResponse get(@PathParam("id") UUID id) {
        return paintingService.get(id, false);
    }
}
