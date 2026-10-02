package com.syndicate.security;

import com.syndicate.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec §51: staying signed in without leaving a long-lived key where a script can read it. */
@TestPropertySource(properties = "syndicate.session.secure-cookie=false")
class SessionIT extends IntegrationTestBase {

    @LocalServerPort int port;

    // a plain client: a RestTemplate will not replay a request the server refused
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private String sessionCookie;
    private String accessToken;

    private HttpResponse<String> post(String path, String json, String cookie) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json")
                .POST(json == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String sessionCookieOf(HttpResponse<String> response) {
        List<String> cookies = response.headers().allValues("set-cookie");
        return cookies.stream().filter(c -> c.startsWith("syndicate_session=") && !c.contains("syndicate_session=;"))
                .findFirst().map(c -> c.split(";")[0]).orElse(null);
    }

    @BeforeEach
    void signUp() throws Exception {
        String email = "session-" + UUID.randomUUID() + "@test.local";
        HttpResponse<String> response = post("/api/auth/register", """
                {"email":"%s","password":"password123","fullName":"Session Tester",
                 "organizationName":"Session Org %s","organizationType":"MERCHANT_BANKER"}
                """.formatted(email, UUID.randomUUID()), null);
        assertThat(response.statusCode()).isEqualTo(201);
        accessToken = response.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        sessionCookie = sessionCookieOf(response);
        assertThat(sessionCookie).isNotNull();
    }

    @Test
    void theSessionCookieCannotBeReadByScriptsAndIsScopedToTheAuthRoutes() throws Exception {
        HttpResponse<String> refreshed = post("/api/auth/refresh", null, sessionCookie);
        assertThat(refreshed.headers().allValues("set-cookie")).anySatisfy(cookie -> assertThat(cookie)
                .contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth"));
    }

    @Test
    void refreshingRotatesTheCookieAndReturnsAUsableToken() throws Exception {
        HttpResponse<String> refreshed = post("/api/auth/refresh", null, sessionCookie);

        assertThat(refreshed.statusCode()).isEqualTo(200);
        assertThat(sessionCookieOf(refreshed)).isNotNull().isNotEqualTo(sessionCookie);

        String token = refreshed.body().replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");
        HttpRequest me = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/auth/me"))
                .header("Authorization", "Bearer " + token).GET().build();
        assertThat(client.send(me, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(200);
    }

    @Test
    void reusingAnAlreadyExchangedCookieEndsEverySessionInThatChain() throws Exception {
        String rotated = sessionCookieOf(post("/api/auth/refresh", null, sessionCookie));

        // the old cookie is replayed: this is either a copy or a theft
        assertThat(post("/api/auth/refresh", null, sessionCookie).statusCode()).isEqualTo(401);
        // and the chain it belonged to is dead, including the cookie that replaced it
        assertThat(post("/api/auth/refresh", null, rotated).statusCode()).isEqualTo(401);
    }

    @Test
    void signingOutEndsTheSession() throws Exception {
        assertThat(post("/api/auth/logout", null, sessionCookie).statusCode()).isEqualTo(204);
        assertThat(post("/api/auth/refresh", null, sessionCookie).statusCode()).isEqualTo(401);
    }

    @Test
    void accessTokensAreShortLived() {
        String payload = new String(Base64.getUrlDecoder().decode(accessToken.split("\\.")[1]));
        long issued = Long.parseLong(payload.replaceAll(".*\"iat\":(\\d+).*", "$1"));
        long expires = Long.parseLong(payload.replaceAll(".*\"exp\":(\\d+).*", "$1"));
        assertThat(expires - issued).as("an access token should not outlive an hour").isLessThanOrEqualTo(3600);
    }
}
