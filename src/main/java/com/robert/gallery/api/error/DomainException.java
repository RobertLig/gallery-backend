package com.robert.gallery.api.error;

import jakarta.ws.rs.core.Response;

public class DomainException extends RuntimeException {

    private final Response.Status status;
    private final String messageKey;

    public DomainException(Response.Status status, String messageKey) {
        super(messageKey);
        this.status = status;
        this.messageKey = messageKey;
    }

    public Response.Status getStatus() {
        return status;
    }

    public String getMessageKey() {
        return messageKey;
    }
}
