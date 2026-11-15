package com.robert.gallery.api;

import com.robert.gallery.domain.Painting;
import com.robert.gallery.domain.PaintingStatus;
import com.robert.gallery.domain.User;
import com.robert.gallery.domain.UserLocale;
import com.robert.gallery.domain.UserRole;
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
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(TestJwtKeyResource.class)
class CartResourceTest {

    @jakarta.inject.Inject
    PaintingRepository paintings;

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
    void cartRequiresAuthenticationAndPersistsUniqueAvailablePaintings() {
        UUID userId = createUser();
        UUID paintingId = createPainting();

        given().when().get("/api/v1/cart").then().statusCode(401);

        given()
                .auth().oauth2(token(userId))
                .contentType("application/json")
                .body("{\"paintingId\":\"" + paintingId + "\"}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(1))
                .body("items[0].paintingId", equalTo(paintingId.toString()))
                .body("items[0].purchasable", equalTo(true))
                .body("items[0].painting.title", equalTo("Cart painting"))
                .body("items[0].addedAt", notNullValue())
                .body("totalMinor", equalTo(125000))
                .body("currency", equalTo("PLN"));

        given()
                .auth().oauth2(token(userId))
                .contentType("application/json")
                .body("{\"paintingId\":\"" + paintingId + "\"}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(1))
                .body("totalMinor", equalTo(125000));

        given()
                .auth().oauth2(token(userId))
                .when().delete("/api/v1/cart/items/" + paintingId)
                .then()
                .statusCode(204);

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/cart")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(0))
                .body("totalMinor", equalTo(0));
    }

    @Test
    void cartDoesNotAddUnavailableOrAuctionedPaintingsAndMarksStaleItems() {
        UUID userId = createUser();
        UUID paintingId = createPainting();
        given()
                .auth().oauth2(token(UUID.randomUUID(), "ADMIN"))
                .when().post("/api/v1/admin/paintings/" + paintingId + "/auction")
                .then()
                .statusCode(200);

        given()
                .auth().oauth2(token(userId))
                .contentType("application/json")
                .body("{\"paintingId\":\"" + paintingId + "\"}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(409);

        UUID stalePaintingId = createPainting();
        given()
                .auth().oauth2(token(userId))
                .contentType("application/json")
                .body("{\"paintingId\":\"" + stalePaintingId + "\"}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(200)
                .body("totalMinor", equalTo(125000));

        given()
                .auth().oauth2(token(UUID.randomUUID(), "ADMIN"))
                .when().post("/api/v1/admin/paintings/" + stalePaintingId + "/auction")
                .then()
                .statusCode(200);

        given()
                .auth().oauth2(token(userId))
                .when().get("/api/v1/cart")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(1))
                .body("items[0].paintingId", equalTo(stalePaintingId.toString()))
                .body("items[0].purchasable", equalTo(false))
                .body("totalMinor", equalTo(0));
    }

    @Test
    void cartRejectsUnknownPaintingsAndMalformedRequests() {
        UUID userId = createUser();

        given()
                .auth().oauth2(token(userId))
                .contentType("application/json")
                .body("{\"paintingId\":\"" + UUID.randomUUID() + "\"}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(404);

        given()
                .auth().oauth2(token(userId))
                .contentType("application/json")
                .body("{}")
                .when().post("/api/v1/cart/items")
                .then()
                .statusCode(400);
    }

    private UUID createPainting() {
        Painting painting = new Painting();
        painting.title = "Cart painting";
        painting.description = "Cart test";
        painting.medium = "Oil";
        painting.widthCm = new BigDecimal("50.00");
        painting.heightCm = new BigDecimal("70.00");
        painting.artworkYear = 2024;
        painting.priceMinor = 125000L;
        painting.status = PaintingStatus.AVAILABLE;
        QuarkusTransaction.requiringNew().run(() -> paintings.persist(painting));
        return painting.id;
    }

    private UUID createUser() {
        UUID id = UUID.randomUUID();
        User user = new User();
        user.id = id;
        user.googleSubject = id.toString();
        user.email = id + "@example.test";
        user.displayName = "Cart user";
        user.role = UserRole.USER;
        user.locale = UserLocale.EN;
        QuarkusTransaction.requiringNew().run(() -> users.persist(user));
        return id;
    }

    private String token(UUID subject) {
        return token(subject, "USER");
    }

    private String token(UUID subject, String role) {
        return Jwt.issuer("gallery-app")
                .subject(subject.toString())
                .groups(role)
                .expiresAt(Instant.now().plusSeconds(600))
                .sign();
    }
}
