package com.robert.gallery.auth;

import com.robert.gallery.api.error.DomainException;

@FunctionalInterface
public interface GoogleTokenVerifier {

    GoogleIdentity verify(String credential) throws DomainException;
}
