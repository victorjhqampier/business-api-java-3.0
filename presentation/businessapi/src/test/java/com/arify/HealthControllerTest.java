package com.arify;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class HealthControllerTest {

    @Test
    void testInfoEndpoint() {
        given()
                .when().get("/")
                .then()
                .statusCode(200)
                .body("service", is("businessapi"))
                .body("status", is("Service Running"))
                .body("environment", notNullValue());
    }

    @Test
    void testPingEndpoint() {
        given()
                .when().get("/ping")
                .then()
                .statusCode(200)
                .body("message", is("pong"))
                .body("timestamp", notNullValue());
    }

    @Test
    void testHealthEndpoint() {
        given()
                .when().get("/health")
                .then()
                .statusCode(200)
                .body("status", is("UP"))
                .body("environment", notNullValue())
                .body("timestamp", notNullValue());
    }
}
