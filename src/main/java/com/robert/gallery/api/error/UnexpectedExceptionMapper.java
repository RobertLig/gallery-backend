package com.robert.gallery.api.error;

import com.robert.gallery.i18n.MessageService;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

@Provider
public class UnexpectedExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger LOG = Logger.getLogger(UnexpectedExceptionMapper.class);

    @Inject
    MessageService messages;

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(Exception exception) {
        LOG.error("Unhandled request failure", exception);
        ApiError error = ApiError.of(
                "error.internal",
                messages.get("error.internal", headers)
        );
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
    }
}
