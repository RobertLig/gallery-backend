package com.robert.gallery.api;

import com.robert.gallery.dto.GalleryContactResponse;
import com.robert.gallery.service.GalleryContactService;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/v1/contact")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class GalleryContactResource {

    @Inject
    GalleryContactService contact;

    @GET
    public GalleryContactResponse getContact() {
        return contact.getAdminContact();
    }
}
