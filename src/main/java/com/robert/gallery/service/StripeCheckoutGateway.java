package com.robert.gallery.service;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class StripeCheckoutGateway implements StripePaymentGateway {

    @ConfigProperty(name = "app.stripe.secret-key")
    String secretKey;

    @ConfigProperty(name = "app.stripe.webhook-secret")
    String webhookSecret;

    @ConfigProperty(name = "frontend.base.url")
    String frontendBaseUrl;

    @Override
    public boolean isConfigured() {
        return isSecretConfigured(secretKey) && isSecretConfigured(webhookSecret);
    }

    @Override
    public StripeCheckoutSession createSession(CheckoutDraft draft) throws StripeException {
        requireConfigured(secretKey, "STRIPE_SECRET_KEY");
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendBaseUrl + draft.successPath()
                        + "?order_id=" + draft.orderId() + "&session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(frontendBaseUrl + draft.cancelPath())
                .setExpiresAt(draft.expiresAt().getEpochSecond())
                .setClientReferenceId(draft.orderId().toString())
                .putMetadata("order_id", draft.orderId().toString())
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.CARD)
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.BLIK)
                .addAllowedPaymentMethodType(SessionCreateParams.AllowedPaymentMethodType.P24);

        for (CheckoutLineItem item : draft.items()) {
            params.addLineItem(SessionCreateParams.LineItem.builder()
                    .setQuantity(1L)
                    .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                            .setCurrency(draft.currency().toLowerCase(Locale.ROOT))
                            .setUnitAmount(item.amountMinor())
                            .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                    .setName(item.name())
                                    .build())
                            .build())
                    .build());
        }

        Session session = Session.create(
                params.build(),
                RequestOptions.builder()
                        .setApiKey(secretKey)
                        .setIdempotencyKey("gallery-order-" + draft.orderId())
                        .build()
        );
        if (session.getUrl() == null || session.getUrl().isBlank()) {
            throw new IllegalStateException("Stripe did not return a Checkout URL");
        }
        return new StripeCheckoutSession(
                session.getId(),
                session.getUrl(),
                Instant.ofEpochSecond(session.getExpiresAt())
        );
    }

    @Override
    public StripeWebhookEvent retrieveSession(String sessionId) throws StripeException {
        requireConfigured(secretKey, "STRIPE_SECRET_KEY");
        Session session = Session.retrieve(sessionId, RequestOptions.builder().setApiKey(secretKey).build());
        String eventType = switch (session.getStatus()) {
            case "expired" -> "checkout.session.expired";
            case "complete" -> "checkout.session.completed";
            default -> "checkout.session.open";
        };
        Map<String, String> metadata = session.getMetadata();
        String orderId = metadata == null ? null : metadata.get("order_id");
        return new StripeWebhookEvent(
                "reconciled_" + session.getId(),
                eventType,
                orderId == null ? null : java.util.UUID.fromString(orderId),
                session.getId(),
                session.getPaymentStatus(),
                session.getAmountTotal(),
                session.getCurrency()
        );
    }

    @Override
    public StripeWebhookEvent verifyWebhook(String payload, String signature) {
        requireConfigured(webhookSecret, "STRIPE_WEBHOOK_SECRET");
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("Stripe-Signature header is required");
        }
        try {
            Event event = Webhook.constructEvent(payload, signature, webhookSecret);
            if (!java.util.Set.of(
                    "checkout.session.completed",
                    "checkout.session.expired",
                    "checkout.session.async_payment_succeeded",
                    "checkout.session.async_payment_failed"
            ).contains(event.getType())) {
                return new StripeWebhookEvent(event.getId(), event.getType(), null, null, null, null, null);
            }

            Optional<StripeObject> stripeObject = event.getDataObjectDeserializer().getObject();
            if (stripeObject.isEmpty() || !(stripeObject.get() instanceof Session session)) {
                throw new IllegalArgumentException("Stripe Checkout session could not be deserialized");
            }
            Map<String, String> metadata = session.getMetadata();
            String orderId = metadata == null ? null : metadata.get("order_id");
            if (orderId == null) {
                return new StripeWebhookEvent(event.getId(), event.getType(), null, session.getId(), null, null, null);
            }
            return new StripeWebhookEvent(
                    event.getId(),
                    event.getType(),
                    java.util.UUID.fromString(orderId),
                    session.getId(),
                    session.getPaymentStatus(),
                    session.getAmountTotal(),
                    session.getCurrency()
            );
        } catch (com.stripe.exception.SignatureVerificationException | IllegalArgumentException exception) {
            throw new IllegalArgumentException("Stripe webhook signature or order reference is invalid", exception);
        }
    }

    private void requireConfigured(String value, String variable) {
        if (!isSecretConfigured(value)) {
            throw new IllegalStateException(variable + " is not configured");
        }
    }

    private boolean isSecretConfigured(String value) {
        return value != null && !value.isBlank() && !"not-configured".equalsIgnoreCase(value);
    }
}
