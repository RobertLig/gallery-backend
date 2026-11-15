package com.robert.gallery.api;

import com.robert.gallery.domain.User;
import com.robert.gallery.domain.UserLocale;
import com.robert.gallery.domain.UserRole;
import com.robert.gallery.repository.UserRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.build.Jwt;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

@QuarkusTest
@QuarkusTestResource(TestJwtKeyResource.class)
class GalleryContactResourceTest {

    @jakarta.inject.Inject
    UserRepository users;

    @Test
    void onlyAuthenticatedUsersCanGetAdminPhoneForPickup() {
        UUID adminId = UUID.randomUUID();
        User admin = new User();
        admin.id = adminId;
        admin.googleSubject = adminId.toString();
        admin.email = "contact@example.test";
        admin.displayName = "Gallery owner";
        admin.phone = "+48123456789";
        admin.role = UserRole.ADMIN;
        admin.locale = UserLocale.EN;
        QuarkusTransaction.requiringNew().run(() -> users.persist(admin));

        given()
                .when().get("/api/v1/contact")
                .then()
                .statusCode(401);

        given()
                .auth().oauth2(token(UUID.randomUUID()))
                .when().get("/api/v1/contact")
                .then()
                .statusCode(200)
                .body("phone", equalTo("+48123456789"))
                .body("collectionOnly", equalTo(true));

        QuarkusTransaction.requiringNew().run(() -> {
            User persistedAdmin = users.findById(adminId);
            persistedAdmin.phone = null;
        });

        given()
                .auth().oauth2(token(UUID.randomUUID()))
                .when().get("/api/v1/contact")
                .then()
                .statusCode(503);
    }

    private String token(UUID subject) {
        return Jwt.issuer("gallery-app")
                .subject(subject.toString())
                .groups("USER")
                .expiresAt(Instant.now().plusSeconds(600))
                .sign();
    }
}
