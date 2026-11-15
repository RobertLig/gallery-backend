package com.robert.gallery.service;

import com.stripe.exception.StripeException;

public interface StripePaymentGateway {

    boolean isConfigured();

    StripeCheckoutSession createSession(CheckoutDraft draft) throws StripeException;

    StripeWebhookEvent retrieveSession(String sessionId) throws StripeException;

    StripeWebhookEvent verifyWebhook(String payload, String signature);
}
