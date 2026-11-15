package com.robert.gallery.api;

import com.robert.gallery.dto.PageResponse;
import com.robert.gallery.dto.PaintingRequest;
import com.robert.gallery.dto.PaintingResponse;
import com.robert.gallery.dto.PhotoOrderRequest;
import com.robert.gallery.dto.PhotoUploadRequest;
import com.robert.gallery.dto.PhotoUploadResponse;
import com.robert.gallery.service.PaintingService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.UUID;

@Path("/api/v1/admin/paintings")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed("ADMIN")
public class AdminPaintingResource {

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
                true, search, medium, minPrice, maxPrice, minYear, maxYear,
                status, sort, page == null ? 0 : page, size == null ? 20 : size
        );
    }

    @GET
    @Path("/{id}")
    public PaintingResponse get(@PathParam("id") UUID id) {
        return paintingService.get(id, true);
    }

    @POST
    public Response create(@NotNull(message = "{error.request.required}") @Valid PaintingRequest request) {
        PaintingResponse created = paintingService.create(request);
        return Response.created(URI.create("/api/v1/admin/paintings/" + created.id()))
                .entity(created)
                .build();
    }

    @PUT
    @Path("/{id}")
    public PaintingResponse update(
            @PathParam("id") UUID id,
            @NotNull(message = "{error.request.required}") @Valid PaintingRequest request
    ) {
        return paintingService.update(id, request);
    }

    @DELETE
    @Path("/{id}")
    public void delete(@PathParam("id") UUID id) {
        paintingService.delete(id);
    }

    @POST
    @Path("/{id}/photos")
    public PhotoUploadResponse presignPhoto(
            @PathParam("id") UUID id,
            @NotNull(message = "{error.request.required}") @Valid PhotoUploadRequest request
    ) {
        return paintingService.presignPhoto(id, request);
    }

    @POST
    @Path("/{id}/photos/{photoId}/complete")
    public PaintingResponse completePhoto(@PathParam("id") UUID id, @PathParam("photoId") UUID photoId) {
        return paintingService.completePhoto(id, photoId);
    }

    @PUT
    @Path("/{id}/photos/order")
    public PaintingResponse reorderPhotos(
            @PathParam("id") UUID id,
            @NotNull(message = "{error.request.required}") @Valid PhotoOrderRequest request
    ) {
        return paintingService.reorderPhotos(id, request);
    }

    @DELETE
    @Path("/{id}/photos/{photoId}")
    public PaintingResponse deletePhoto(@PathParam("id") UUID id, @PathParam("photoId") UUID photoId) {
        return paintingService.deletePhoto(id, photoId);
    }
}
