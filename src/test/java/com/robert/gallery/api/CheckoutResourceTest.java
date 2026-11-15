package com.robert.gallery.api;

import com.robert.gallery.domain.Auction;
import com.robert.gallery.domain.Bid;
import com.robert.gallery.domain.Painting;
import com.robert.gallery.domain.PaintingStatus;
import com.robert.gallery.domain.User;
import com.robert.gallery.domain.UserLocale;
import com.robert.gallery.domain.UserRole;
import com.robert.gallery.repository.AuctionRepository;
import com.robert.gallery.repository.BidRepository;
import com.robert.gallery.repository.PaintingRepository;
import com.robert.gallery.repository.UserRepository;
import com.robert.gallery.service.CheckoutDraft;
import com.robert.gallery.service.StripeCheckoutSession;
import com.robert.gallery.service.StripeCheckoutGateway;
import com.robert.gallery.service.StripeWebhookEvent;
import com.robert.gallery.storage.PaintingStorage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(TestJwtKeyResource.class)
class CheckoutResourceTest {

    @jakarta.inject.Inject
    PaintingRepository paintings;

    @jakarta.inject.Inject
    UserRepository users;

    @jakarta.inject.Inject
    AuctionRepository auctions;

    @jakarta.inject.Inject
    BidRepository bids;

    private FakeStripeGateway stripe;

    @BeforeEach
    void setUp() {
        QuarkusMock.installMockForType(new PaintingStorage() {
            @Override
            public String presignRead(String key) {
                return "https://storage.example.test/read";
            }

            @Override
            public String presignUpload(String key, String contentType, long size) {
                return "https://storage.example.test/upload";
            }

            @Override
            public HeadObjectResponse verifyUploadedObject(String key) {
                return HeadObjectResponse.builder().contentLength(1024L).contentType("image/jpeg").build();
            }

            @Override
            public void deleteObject(String key) {
            }
        }, PaintingStorage.class);
        stripe = new FakeStripeGateway();
        QuarkusMock.installMockForType(stripe, StripeCheckoutGateway.class);
    }

    @Test
    void cartCheckoutUsesServerPriceAndOnlyWebhookMarksOrderPaid() {
        UUID userId = createUser();
        UUID paintingId = createPainting();
        addToCart(userId, paintingId);

        String orderId = given()
                .auth().oauth2(token(userId))
                .when().post("/api/v1/checkout/cart")
                .then()
                .statusCode(200)
                .body("checkoutUrl", equalTo("https://checkout.stripe.test/session"))
                .body("expiresAt", notNullValue())
                .body("orderId", notNullValue())
                .extract().path("orderId");

        org.junit.jupiter.api.Assertions.assertEquals(125000, stripe.draft.totalMinor());
        org.junit.jupiter.api.Assertions.assertEquals("PLN", stripe.draft.currency());
        org.junit.jupiter.api.Assertions.assertEquals("Checkout painting", stripe.draft.items().getFirst().name());
        org.junit.jupiter.api.Assertions.assertEquals(125000, stripe.draft.items().getFirst().amountMinor());

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/checkout/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("PENDING_PAYMENT"))
                .body("totalMinor", equalTo(125000))
                .body("currency", equalTo("PLN"));

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/checkout/orders/pending")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(orderId));

        given()
                .auth().oauth2(token(userId))
                .when().post("/api/v1/checkout/orders/" + orderId + "/session")
                .then()
                .statusCode(200)
                .body("checkoutUrl", equalTo("https://checkout.stripe.test/session"));

        UUID anotherUserId = createUser();
        given()
                .auth().oauth2(token(anotherUserId))
                .when().get("/api/v1/checkout/orders/" + orderId)
                .then()
                .statusCode(404);

        given()
                .auth().oauth2(token(anotherUserId))
                .contentType("application/json")
                .body("{\"paintingId\":\"" + paintingId + "\"}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(409);

        stripe.event = new StripeWebhookEvent(
                "evt_test_paid",
                "checkout.session.completed",
                UUID.fromString(orderId),
                stripe.sessionId,
                "paid",
                125000L,
                "pln"
        );
        given()
                .header("Stripe-Signature", "valid-test-signature")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/payments/stripe/webhook")
                .then()
                .statusCode(204);

        given()
                .header("Stripe-Signature", "valid-test-signature")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/payments/stripe/webhook")
                .then()
                .statusCode(204);

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/checkout/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("PAID"));

        given()
                .when().get("/api/v1/paintings/" + paintingId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SOLD"));

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/cart")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(0))
                .body("totalMinor", equalTo(0));
    }

    @Test
    void auctionCheckoutIsRestrictedToWinnerAndHasFixedPaymentDeadline() {
        UUID winnerId = createUser();
        UUID loserId = createUser();
        UUID paintingId = createPainting();
        UUID auctionId = createEndedAuction(paintingId, winnerId, Instant.now().minusSeconds(3600));

        given()
                .auth().oauth2(token(loserId))
                .when().post("/api/v1/auctions/" + auctionId + "/checkout")
                .then()
                .statusCode(403);

        given()
                .auth().oauth2(token(winnerId))
                .when().post("/api/v1/auctions/" + auctionId + "/checkout")
                .then()
                .statusCode(200)
                .body("orderId", notNullValue())
                .body("checkoutUrl", equalTo("https://checkout.stripe.test/session"));

        org.junit.jupiter.api.Assertions.assertEquals(140000, stripe.draft.totalMinor());
        org.junit.jupiter.api.Assertions.assertTrue(stripe.draft.expiresAt().isAfter(Instant.now().plusSeconds(22 * 60 * 60)));
        org.junit.jupiter.api.Assertions.assertTrue(stripe.draft.expiresAt().isBefore(Instant.now().plusSeconds(24 * 60 * 60)));

        UUID expiredPaintingId = createPainting();
        UUID expiredAuctionId = createEndedAuction(
                expiredPaintingId,
                winnerId,
                Instant.now().minusSeconds(25 * 60 * 60)
        );
        given()
                .auth().oauth2(token(winnerId))
                .when().post("/api/v1/auctions/" + expiredAuctionId + "/checkout")
                .then()
                .statusCode(409);
    }

    @Test
    void emptyCartAndInvalidWebhookSignatureAreRejected() {
        UUID userId = createUser();
        given()
                .auth().oauth2(token(userId))
                .when().post("/api/v1/checkout/cart")
                .then()
                .statusCode(409);

        stripe.invalidSignature = true;
        given()
                .header("Stripe-Signature", "invalid")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/payments/stripe/webhook")
                .then()
                .statusCode(400);
    }

    @Test
    void expiredStripeSessionReleasesPaintingForAnotherCheckout() {
        UUID userId = createUser();
        UUID paintingId = createPainting();
        addToCart(userId, paintingId);
        String expiredOrderId = given()
                .auth().oauth2(token(userId))
                .when().post("/api/v1/checkout/cart")
                .then()
                .statusCode(200)
                .extract().path("orderId");

        stripe.event = new StripeWebhookEvent(
                "evt_test_expired",
                "checkout.session.expired",
                UUID.fromString(expiredOrderId),
                stripe.sessionId,
                "unpaid",
                null,
                null
        );
        given()
                .header("Stripe-Signature", "valid-test-signature")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/payments/stripe/webhook")
                .then()
                .statusCode(204);

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/checkout/orders/" + expiredOrderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("EXPIRED"));

        given()
                .auth().oauth2(token(userId))
                .when().post("/api/v1/checkout/cart")
                .then()
                .statusCode(200)
                .body("orderId", notNullValue());
    }

    @Test
    void asynchronousPaymentRemainsPendingUntilStripeConfirmsIt() {
        UUID userId = createUser();
        UUID paintingId = createPainting();
        addToCart(userId, paintingId);
        String orderId = given()
                .auth().oauth2(token(userId))
                .when().post("/api/v1/checkout/cart")
                .then()
                .statusCode(200)
                .extract().path("orderId");

        stripe.event = new StripeWebhookEvent(
                "evt_test_processing",
                "checkout.session.completed",
                UUID.fromString(orderId),
                stripe.sessionId,
                "unpaid",
                125000L,
                "pln"
        );
        given()
                .header("Stripe-Signature", "valid-test-signature")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/payments/stripe/webhook")
                .then()
                .statusCode(204);

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/checkout/orders/" + orderId)
                .then()
                .statusCode(200)
                .body("status", equalTo("ASYNC_PAYMENT_PENDING"));

        stripe.event = new StripeWebhookEvent(
                "evt_test_async_paid",
                "checkout.session.async_payment_succeeded",
                UUID.fromString(orderId),
                stripe.sessionId,
                "paid",
                125000L,
                "pln"
        );
        given()
                .header("Stripe-Signature", "valid-test-signature")
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/payments/stripe/webhook")
                .then()
                .statusCode(204);

        given()
                .when().get("/api/v1/paintings/" + paintingId)
                .then()
                .statusCode(200)
                .body("status", equalTo("SOLD"));
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.id = id;
        user.googleSubject = id.toString();
        user.email = id + "@example.test";
        user.displayName = "Checkout user";
        user.role = UserRole.USER;
        user.locale = UserLocale.EN;
        QuarkusTransaction.requiringNew().run(() -> users.persist(user));
        return id;
    }

    private UUID createPainting() {
        Painting painting = new Painting();
        painting.title = "Checkout painting";
        painting.description = "Checkout test";
        painting.medium = "Oil";
        painting.widthCm = new BigDecimal("50.00");
        painting.heightCm = new BigDecimal("70.00");
        painting.artworkYear = 2024;
        painting.priceMinor = 125000L;
        painting.status = PaintingStatus.AVAILABLE;
        QuarkusTransaction.requiringNew().run(() -> paintings.persist(painting));
        return painting.id;
    }

    private UUID createEndedAuction(UUID paintingId, UUID bidderId, Instant endedAt) {
        Auction auction = new Auction();
        auction.id = UUID.randomUUID();
        auction.startedAt = endedAt.minusSeconds(3600);
        auction.endsAt = endedAt;
        auction.startingPriceMinor = 125000;
        Bid bid = new Bid();
        bid.amountMinor = 140000;
        bid.placedAt = endedAt.minusSeconds(1800);
        QuarkusTransaction.requiringNew().run(() -> {
            auction.painting = paintings.findById(paintingId);
            auctions.persist(auction);
            bid.auction = auction;
            bid.bidder = users.findById(bidderId);
            bids.persist(bid);
        });
        return auction.id;
    }

    private void addToCart(UUID userId, UUID paintingId) {
        given()
                .auth().oauth2(token(userId))
                .contentType("application/json")
                .body("{\"paintingId\":\"" + paintingId + "\"}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(200);
    }

    private String token(UUID subject) {
        return Jwt.issuer("gallery-app")
                .subject(subject.toString())
                .groups("USER")
                .expiresAt(Instant.now().plusSeconds(600))
                .sign();
    }

    private static class FakeStripeGateway extends StripeCheckoutGateway {

        CheckoutDraft draft;
        StripeWebhookEvent event;
        String sessionId;
        boolean invalidSignature;

        @Override
        public boolean isConfigured() {
            return true;
        }

        @Override
        public StripeCheckoutSession createSession(CheckoutDraft draft) {
            this.draft = draft;
            sessionId = "cs_test_" + draft.orderId();
            return new StripeCheckoutSession(
                    sessionId,
                    "https://checkout.stripe.test/session",
                    draft.expiresAt()
            );
        }

        @Override
        public StripeWebhookEvent retrieveSession(String sessionId) {
            return event;
        }

        @Override
        public StripeWebhookEvent verifyWebhook(String payload, String signature) {
            if (invalidSignature || !"valid-test-signature".equals(signature)) {
                throw new IllegalArgumentException("Invalid test signature");
            }
            return event;
        }
    }
}
