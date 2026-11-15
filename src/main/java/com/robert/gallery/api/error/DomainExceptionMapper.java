package com.robert.gallery.api.error;

import com.robert.gallery.i18n.MessageService;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class DomainExceptionMapper implements ExceptionMapper<DomainException> {

    @Inject
    MessageService messages;

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(DomainException exception) {
        ApiError error = ApiError.of(
                exception.getMessageKey(),
                messages.get(exception.getMessageKey(), headers)
        );
        return Response.status(exception.getStatus()).entity(error).build();
    }
}
