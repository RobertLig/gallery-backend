package com.robert.gallery.api.error;

import com.robert.gallery.i18n.MessageService;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Map;
import java.util.TreeMap;

@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Inject
    MessageService messages;

    @Context
    HttpHeaders headers;

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        Map<String, String> fieldErrors = new TreeMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            String template = violation.getMessageTemplate();
            String message = template.startsWith("{") && template.endsWith("}")
                    ? messages.get(template.substring(1, template.length() - 1), headers)
                    : violation.getMessage();
            fieldErrors.put(violation.getPropertyPath().toString(), message);
        }

        ApiError error = new ApiError(
                "validation.failed",
                messages.get("error.validation", headers),
                fieldErrors,
                java.time.Instant.now()
        );
        return Response.status(Response.Status.BAD_REQUEST).entity(error).build();
    }
}
