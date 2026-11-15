package com.robert.gallery.service;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.dto.CheckoutResponse;
import com.stripe.exception.StripeException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import io.quarkus.scheduler.Scheduled;

import java.time.Instant;
import java.util.UUID;

@ApplicationScoped
public class CheckoutService {

    private static final Logger LOG = Logger.getLogger(CheckoutService.class);

    @Inject
    CheckoutOrderService orders;

    @Inject
    StripePaymentGateway stripe;

    @Scheduled(every = "1m", delayed = "1m")
    void reconcileExpiredCheckoutSessions() {
        if (!stripe.isConfigured()) {
            return;
        }
        for (PendingCheckoutSession pending : orders.expiredPendingSessions(Instant.now().minusSeconds(60), 100)) {
            if (pending.stripeSessionId() == null) {
                orders.expirePendingOrder(pending.orderId());
                continue;
            }
            try {
                StripeWebhookEvent event = stripe.retrieveSession(pending.stripeSessionId());
                orders.processWebhook(event);
            } catch (StripeException exception) {
                LOG.errorf(exception, "Could not reconcile expired Stripe Checkout session for order %s", pending.orderId());
            } catch (RuntimeException exception) {
                LOG.errorf(exception, "Could not apply Stripe Checkout reconciliation for order %s", pending.orderId());
            }
        }
    }

    public CheckoutResponse startCartCheckout(String userSubject) {
        return createCheckout(orders.createCartOrder(userSubject));
    }

    public CheckoutResponse startAuctionCheckout(String userSubject, UUID auctionId) {
        return createCheckout(orders.createAuctionOrder(userSubject, auctionId));
    }

    public CheckoutResponse retryCheckout(String userSubject, UUID orderId) {
        return createCheckout(orders.prepareRetry(userSubject, orderId));
    }

    public void handleWebhook(String payload, String signature) {
        StripeWebhookEvent event;
        try {
            event = stripe.verifyWebhook(payload, signature);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw new DomainException(Response.Status.BAD_REQUEST, "error.payment.webhook.invalid");
        }
        orders.processWebhook(event);
    }

    private CheckoutResponse createCheckout(CheckoutDraft draft) {
        if (!stripe.isConfigured()) {
            orders.failOrder(draft.orderId());
            throw new DomainException(Response.Status.SERVICE_UNAVAILABLE, "error.payment.unconfigured");
        }
        try {
            StripeCheckoutSession session = stripe.createSession(draft);
            orders.attachStripeSession(draft.orderId(), session);
            return new CheckoutResponse(draft.orderId(), session.url(), session.expiresAt());
        } catch (StripeException exception) {
            LOG.errorf(exception, "Could not create Stripe Checkout session for order %s", draft.orderId());
            throw new DomainException(Response.Status.SERVICE_UNAVAILABLE, "error.payment.unavailable");
        }
    }
}
