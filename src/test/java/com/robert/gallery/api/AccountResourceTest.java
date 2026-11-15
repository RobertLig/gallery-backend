package com.robert.gallery.api;

import com.robert.gallery.api.error.DomainException;
import com.robert.gallery.auth.GoogleIdentity;
import com.robert.gallery.auth.GoogleIdTokenVerifierService;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
@QuarkusTestResource(TestJwtKeyResource.class)
class AccountResourceTest {

    @Inject
    JWTParser jwtParser;

    private String email;

    @BeforeEach
    void mockGoogleTokenVerifier() {
        String subject = UUID.randomUUID().toString();
        email = subject + "@example.test";
        GoogleIdTokenVerifierService verifier = new GoogleIdTokenVerifierService() {
            @Override
            public GoogleIdentity verify(String credential) {
                if (!"valid-google-credential".equals(credential)) {
                    throw new DomainException(Response.Status.UNAUTHORIZED, "error.auth.google.invalid");
                }
                return new GoogleIdentity(subject, email, "Gallery User", null);
            }
        };
        QuarkusMock.installMockForType(verifier, GoogleIdTokenVerifierService.class);
    }

    @Test
    void googleLoginIssuesAccessTokenAndProfileUsesIt() throws Exception {
        String accessToken = given()
                .contentType("application/json")
                .header("Accept-Language", "pl")
                .body("{\"credential\":\"valid-google-credential\"}")
                .when().post("/api/v1/auth/google")
                .then()
                .statusCode(200)
                .body("accessToken", notNullValue())
                .body("expiresIn", equalTo(900))
                .body("user.email", equalTo(email))
                .body("user.locale", equalTo("pl"))
                .body("user.role", equalTo("USER"))
                .cookie("gallery_refresh", notNullValue())
                .extract().path("accessToken");

        jwtParser.parse(accessToken);
        given()
                .auth().oauth2(accessToken)
                .when().get("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("email", equalTo(email))
                .body("locale", equalTo("pl"));

        given()
                .auth().oauth2(accessToken)
                .contentType("application/json")
                .body("{\"phone\":\"+48123456789\"}")
                .when().patch("/api/v1/users/me")
                .then()
                .statusCode(200)
                .body("phone", equalTo("+48123456789"));

        given()
                .auth().oauth2(accessToken)
                .contentType("application/json")
                .header("Accept-Language", "pl")
                .body("{\"phone\":\"123\"}")
                .when().patch("/api/v1/users/me")
                .then()
                .statusCode(400)
                .body("fieldErrors", notNullValue());
    }

    @Test
    void refreshRotatesCookieAndRejectsReuse() {
        String originalRefreshToken = given()
                .contentType("application/json")
                .body("{\"credential\":\"valid-google-credential\"}")
                .when().post("/api/v1/auth/google")
                .then()
                .statusCode(200)
                .extract().cookie("gallery_refresh");

        String rotatedRefreshToken = given()
                .contentType("application/json")
                .cookie("gallery_refresh", originalRefreshToken)
                .when().post("/api/v1/auth/refresh")
                .then()
                .statusCode(200)
                .cookie("gallery_refresh", not(equalTo(originalRefreshToken)))
                .extract().cookie("gallery_refresh");

        given()
                .contentType("application/json")
                .cookie("gallery_refresh", originalRefreshToken)
                .when().post("/api/v1/auth/refresh")
                .then()
                .statusCode(401);

        given()
                .contentType("application/json")
                .cookie("gallery_refresh", rotatedRefreshToken)
                .when().post("/api/v1/auth/logout")
                .then()
                .statusCode(204);
    }

    @Test
    void profileRequiresAuthentication() {
        given()
                .when().get("/api/v1/users/me")
                .then()
                .statusCode(401);
    }

    @Test
    void googleCredentialIsRequiredAndLocalized() {
        given()
                .contentType("application/json")
                .header("Accept-Language", "pl")
                .body("{\"credential\":\" \"}")
                .when().post("/api/v1/auth/google")
                .then()
                .statusCode(400)
                .body("fieldErrors", notNullValue())
                .body("message", equalTo("Żądanie zawiera nieprawidłowe wartości."));
    }
}
