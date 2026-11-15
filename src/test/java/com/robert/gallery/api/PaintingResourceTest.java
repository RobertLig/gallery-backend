package com.robert.gallery.api;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import com.robert.gallery.storage.PaintingStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(TestJwtKeyResource.class)
class PaintingResourceTest {

    @BeforeEach
    void stubStorage() {
        QuarkusMock.installMockForType(new PaintingStorage() {
            @Override
            public String presignUpload(String key, String contentType, long size) {
                return "https://storage.example.test/upload";
            }

            @Override
            public String presignRead(String key) {
                return "https://storage.example.test/read";
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
    void publicCatalogHidesDraftsAndSupportsSearchAndPaging() {
        String suffix = UUID.randomUUID().toString();
        String admin = token("ADMIN");
        String availableId = createPainting(admin, "Sunlit " + suffix, "AVAILABLE");
        String draftId = createPainting(admin, "Secret " + suffix, "DRAFT");

        given()
                .queryParam("q", suffix)
                .queryParam("size", 1)
                .when().get("/api/v1/paintings")
                .then()
                .statusCode(200)
                .body("items.size()", equalTo(1))
                .body("items[0].status", equalTo("AVAILABLE"))
                .body("items[0].currency", equalTo("PLN"))
                .body("totalItems", equalTo(1))
                .body("totalPages", equalTo(1));

        given()
                .when().get("/api/v1/paintings/" + availableId)
                .then()
                .statusCode(200)
                .body("title", equalTo("Sunlit " + suffix))
                .body("photos.size()", equalTo(0));

        given()
                .when().get("/api/v1/paintings/" + draftId)
                .then()
                .statusCode(404);

        given()
                .auth().oauth2(admin)
                .when().get("/api/v1/admin/paintings/" + draftId)
                .then()
                .statusCode(200)
                .body("status", equalTo("DRAFT"));

        given()
                .queryParam("page", -1)
                .when().get("/api/v1/paintings")
                .then()
                .statusCode(400);

        given()
                .auth().oauth2(token("ADMIN"))
                .contentType("application/json")
                .body(paintingBody("Invalid status " + UUID.randomUUID(), "NOT_A_STATUS"))
                .when().post("/api/v1/admin/paintings")
                .then()
                .statusCode(400)
                .body("fieldErrors.status", notNullValue());
    }

    @Test
    void catalogWritesRequireAdminRole() {
        String body = paintingBody("Forbidden " + UUID.randomUUID(), "AVAILABLE");
        given()
                .auth().oauth2(token("USER"))
                .contentType("application/json")
                .body(body)
                .when().post("/api/v1/admin/paintings")
                .then()
                .statusCode(403);

        given()
                .contentType("application/json")
                .body(body)
                .when().post("/api/v1/admin/paintings")
                .then()
                .statusCode(401);
    }

    @Test
    void adminCanEditDeleteAndPresignLimitedImages() {
        String admin = token("ADMIN");
        String paintingId = createPainting(admin, "Editable " + UUID.randomUUID(), "DRAFT");

        given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .body(paintingBody("Edited " + UUID.randomUUID(), "AVAILABLE"))
                .when().put("/api/v1/admin/paintings/" + paintingId)
                .then()
                .statusCode(200)
                .body("status", equalTo("AVAILABLE"));

        given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .body("{\"contentType\":\"image/gif\",\"fileSize\":100,\"filename\":\"test.gif\"}")
                .when().post("/api/v1/admin/paintings/" + paintingId + "/photos")
                .then()
                .statusCode(400);

        given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .body("{\"contentType\":\"image/jpeg\",\"fileSize\":5000001,\"filename\":\"large.jpg\"}")
                .when().post("/api/v1/admin/paintings/" + paintingId + "/photos")
                .then()
                .statusCode(400);

        String firstPhotoId = given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .body("{\"contentType\":\"image/jpeg\",\"fileSize\":1024,\"filename\":\"painting.jpg\"}")
                .when().post("/api/v1/admin/paintings/" + paintingId + "/photos")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("objectKey", greaterThan(""))
                .body("uploadUrl", greaterThan(""))
                .body("expiresInSeconds", equalTo(300))
                .body("headers.Content-Type", equalTo("image/jpeg"))
                .extract().path("id");

        given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .when().post("/api/v1/admin/paintings/" + paintingId + "/photos/" + firstPhotoId + "/complete")
                .then()
                .statusCode(200)
                .body("photos.size()", equalTo(1));

        String secondPhotoId = given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .body("{\"contentType\":\"image/jpeg\",\"fileSize\":1024,\"filename\":\"painting-2.jpg\"}")
                .when().post("/api/v1/admin/paintings/" + paintingId + "/photos")
                .then()
                .statusCode(200)
                .extract().path("id");

        given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .when().post("/api/v1/admin/paintings/" + paintingId + "/photos/" + secondPhotoId + "/complete")
                .then()
                .statusCode(200)
                .body("photos.size()", equalTo(2));

        given()
                .auth().oauth2(admin)
                .contentType("application/json")
                .body("{\"photoIds\":[\"" + secondPhotoId + "\",\"" + firstPhotoId + "\"]}")
                .when().put("/api/v1/admin/paintings/" + paintingId + "/photos/order")
                .then()
                .statusCode(200)
                .body("photos[0].id", equalTo(secondPhotoId))
                .body("photos[0].sortOrder", equalTo(0));

        given()
                .auth().oauth2(admin)
                .when().delete("/api/v1/admin/paintings/" + paintingId + "/photos/" + firstPhotoId)
                .then()
                .statusCode(200)
                .body("photos.size()", equalTo(1))
                .body("photos[0].sortOrder", equalTo(0));

        given()
                .auth().oauth2(admin)
                .when().delete("/api/v1/admin/paintings/" + paintingId)
                .then()
                .statusCode(204);

        given()
                .when().get("/api/v1/paintings/" + paintingId)
                .then()
                .statusCode(404);
    }

    private String createPainting(String accessToken, String title, String status) {
        return given()
                .auth().oauth2(accessToken)
                .contentType("application/json")
                .body(paintingBody(title, status))
                .when().post("/api/v1/admin/paintings")
                .then()
                .statusCode(201)
                .extract().path("id");
    }

    private String paintingBody(String title, String status) {
        return """
                {
                  "title": "%s",
                  "description": "Original painting description",
                  "medium": "Oil on canvas",
                  "widthCm": 50.00,
                  "heightCm": 70.00,
                  "artworkYear": 2024,
                  "priceMinor": 125000,
                  "status": "%s"
                }
                """.formatted(title, status);
    }

    private String token(String role) {
        return Jwt.issuer("gallery-app")
                .subject(UUID.randomUUID().toString())
                .groups(role)
                .expiresAt(Instant.now().plusSeconds(600))
                .sign();
    }
}
