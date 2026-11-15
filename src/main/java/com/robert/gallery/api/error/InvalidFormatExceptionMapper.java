package com.robert.gallery.api.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.robert.gallery.i18n.MessageService;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;

@Provider
public class InvalidFormatExceptionMapper implements ExceptionMapper<InvalidFormatException> {

    @Inject
    MessageService messages;

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(InvalidFormatException exception) {
        String field = exception.getPath().stream()
                .map(reference -> {
                    if (reference.getFieldName() != null) {
                        return reference.getFieldName();
                    }
                    return reference.getIndex() >= 0 ? "[" + reference.getIndex() + "]" : "";
                })
                .collect(java.util.stream.Collectors.joining("."));
        ApiError error = new ApiError(
                "validation.invalid_format",
                messages.get("error.validation", headers),
                field.isBlank()
                        ? Map.of()
                        : Map.of(field, messages.get("error.request.invalid", headers)),
                java.time.Instant.now()
        );
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }
}
