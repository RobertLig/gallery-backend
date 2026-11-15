package com.robert.gallery.api;

import com.robert.gallery.domain.Auction;
import com.robert.gallery.domain.Painting;
import com.robert.gallery.domain.PaintingStatus;
import com.robert.gallery.domain.User;
import com.robert.gallery.domain.UserLocale;
import com.robert.gallery.domain.UserRole;
import com.robert.gallery.repository.AuctionRepository;
import com.robert.gallery.repository.PaintingRepository;
import com.robert.gallery.repository.UserRepository;
import com.robert.gallery.storage.PaintingStorage;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.not;

@QuarkusTest
@QuarkusTestResource(TestJwtKeyResource.class)
class AuctionResourceTest {

    @jakarta.inject.Inject
    PaintingRepository paintings;

    @jakarta.inject.Inject
    AuctionRepository auctions;

    @jakarta.inject.Inject
    UserRepository users;

    @BeforeEach
    void stubStorage() {
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
    }

    @Test
    void adminStartsOneHourAuctionAndPublicCanBrowseIt() {
        String paintingId = createPainting("AVAILABLE");
        String auctionId = given()
                .auth().oauth2(token("ADMIN", UUID.randomUUID()))
                .when().post("/api/v1/admin/paintings/" + paintingId + "/auction")
                .then()
                .statusCode(200)
                .body("paintingId", equalTo(paintingId))
                .body("startingPriceMinor", equalTo(125000))
                .body("currentPriceMinor", equalTo(125000))
                .body("currency", equalTo("PLN"))
                .body("bidCount", equalTo(0))
                .body("status", equalTo("ACTIVE"))
                .body("id", notNullValue())
                .extract().path("id");

        given()
                .when().get("/api/v1/auctions/" + auctionId)
                .then()
                .statusCode(200)
                .body("id", equalTo(auctionId))
                .body("status", equalTo("ACTIVE"))
                .body("endsAt", notNullValue());

        given()
                .when().get("/api/v1/auctions")
                .then()
                .statusCode(200)
                .body("items.find { it.id == '" + auctionId + "' }.status", equalTo("ACTIVE"));

        given()
                .auth().oauth2(token("ADMIN", UUID.randomUUID()))
                .when().post("/api/v1/admin/paintings/" + paintingId + "/auction")
                .then()
                .statusCode(409);

        given()
                .auth().oauth2(token("USER", UUID.randomUUID()))
                .contentType("application/json")
                .body(paintingBody("AVAILABLE"))
                .when().put("/api/v1/admin/paintings/" + paintingId)
                .then()
                .statusCode(403);

        given()
                .auth().oauth2(token("ADMIN", UUID.randomUUID()))
                .contentType("application/json")
                .body(paintingBody("AVAILABLE"))
                .when().put("/api/v1/admin/paintings/" + paintingId)
                .then()
                .statusCode(409);

        given()
                .auth().oauth2(token("ADMIN", UUID.randomUUID()))
                .when().delete("/api/v1/admin/paintings/" + paintingId)
                .then()
                .statusCode(409);

        Instant start = Instant.parse(given().when().get("/api/v1/auctions/" + auctionId)
                .then().statusCode(200).extract().path("startedAt"));
        Instant end = Instant.parse(given().when().get("/api/v1/auctions/" + auctionId)
                .then().statusCode(200).extract().path("endsAt"));
        org.junit.jupiter.api.Assertions.assertEquals(3600, end.getEpochSecond() - start.getEpochSecond());
    }

    @Test
    void onlyAdminsCanAuctionAvailablePaintings() {
        String availablePaintingId = createPainting("AVAILABLE");
        String draftPaintingId = createPainting("DRAFT");

        given()
                .when().post("/api/v1/admin/paintings/" + availablePaintingId + "/auction")
                .then()
                .statusCode(401);

        given()
                .auth().oauth2(token("USER", UUID.randomUUID()))
                .when().post("/api/v1/admin/paintings/" + availablePaintingId + "/auction")
                .then()
                .statusCode(403);

        given()
                .auth().oauth2(token("ADMIN", UUID.randomUUID()))
                .when().post("/api/v1/admin/paintings/" + draftPaintingId + "/auction")
                .then()
                .statusCode(409);
    }

    @Test
    void signedInUsersCanBidAboveCurrentPriceAndBidsAreSerialized() {
        String paintingId = createPainting("AVAILABLE");
        String auctionId = startAuction(paintingId);
        UUID userId = createUser();

        given()
                .auth().oauth2(token("USER", userId))
                .contentType("application/json")
                .body("{\"amountMinor\":130000}")
                .when().post("/api/v1/auctions/" + UUID.randomUUID() + "/bids")
                .then()
                .statusCode(404);

        given()
                .contentType("application/json")
                .body("{\"amountMinor\":130000}")
                .when().post("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(401);

        given()
                .auth().oauth2(token("USER", UUID.randomUUID()))
                .contentType("application/json")
                .body("{\"amountMinor\":130000}")
                .when().post("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(401);

        given()
                .auth().oauth2(token("USER", userId))
                .contentType("application/json")
                .body("{\"amountMinor\":125000}")
                .when().post("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(409);

        given()
                .auth().oauth2(token("USER", userId))
                .contentType("application/json")
                .body("{\"amountMinor\":130000}")
                .when().post("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(200)
                .body("currentPriceMinor", equalTo(130000))
                .body("bidCount", equalTo(1));

        given()
                .auth().oauth2(token("USER", createUser()))
                .contentType("application/json")
                .body("{\"amountMinor\":129999}")
                .when().post("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(409);

        given()
                .auth().oauth2(token("USER", userId))
                .contentType("application/json")
                .body("{\"amountMinor\":140000}")
                .when().post("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(200)
                .body("currentPriceMinor", equalTo(140000))
                .body("bidCount", equalTo(2));

        given()
                .when().get("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].amountMinor", equalTo(140000))
                .body("[1].amountMinor", equalTo(130000))
                .body("[0].placedAt", notNullValue());
    }

    @Test
    void endedAuctionsCannotReceiveBidsAndDoNotAppearInActiveList() {
        UUID paintingId = UUID.fromString(createPainting("AVAILABLE"));
        UUID auctionId = UUID.randomUUID();
        UUID bidderId = createUser();
        QuarkusTransaction.requiringNew().run(() -> {
            Painting painting = paintings.findById(paintingId);
            Auction auction = new Auction();
            auction.id = auctionId;
            auction.painting = painting;
            auction.startingPriceMinor = painting.priceMinor;
            auction.startedAt = Instant.now().minusSeconds(7200);
            auction.endsAt = Instant.now().minusSeconds(3600);
            auctions.persist(auction);
        });

        given()
                .when().get("/api/v1/auctions/" + auctionId)
                .then()
                .statusCode(200)
                .body("status", equalTo("ENDED"));

        given()
                .when().get("/api/v1/auctions")
                .then()
                .statusCode(200)
                .body("items.id", not(hasItem(auctionId.toString())));

        given()
                .auth().oauth2(token("USER", bidderId))
                .contentType("application/json")
                .body("{\"amountMinor\":130000}")
                .when().post("/api/v1/auctions/" + auctionId + "/bids")
                .then()
                .statusCode(409);
    }

    private String createPainting(String status) {
        Painting painting = new Painting();
        painting.title = "Auction painting " + UUID.randomUUID();
        painting.description = "Auction test";
        painting.medium = "Oil";
        painting.widthCm = new BigDecimal("50.00");
        painting.heightCm = new BigDecimal("70.00");
        painting.artworkYear = 2024;
        painting.priceMinor = 125000L;
        painting.status = PaintingStatus.valueOf(status);
        QuarkusTransaction.requiringNew().run(() -> paintings.persist(painting));
        return painting.id.toString();
    }

    private String startAuction(String paintingId) {
        return given()
                .auth().oauth2(token("ADMIN", UUID.randomUUID()))
                .when().post("/api/v1/admin/paintings/" + paintingId + "/auction")
                .then()
                .statusCode(200)
                .extract().path("id");
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.id = id;
        user.googleSubject = id.toString();
        user.email = id + "@example.test";
        user.displayName = "Auction bidder";
        user.role = UserRole.USER;
        user.locale = UserLocale.EN;
        QuarkusTransaction.requiringNew().run(() -> users.persist(user));
        return id;
    }

    private String token(String role, UUID subject) {
        return Jwt.issuer("gallery-app")
                .subject(subject.toString())
                .groups(role)
                .expiresAt(Instant.now().plusSeconds(600))
                .sign();
    }

    private String paintingBody(String status) {
        return """
                {
                  "title": "Auction painting",
                  "description": "Original painting description",
                  "medium": "Oil on canvas",
                  "widthCm": 50.00,
                  "heightCm": 70.00,
                  "artworkYear": 2024,
                  "priceMinor": 125000,
                  "status": "%s"
                }
                """.formatted(status);
    }
}
