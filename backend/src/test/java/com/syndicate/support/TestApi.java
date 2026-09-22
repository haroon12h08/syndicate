package com.syndicate.support;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;

import java.util.Map;
import java.util.UUID;

/**
 * Thin HTTP client for integration tests. Everything goes through the public API so tests
 * exercise the same authorization paths as real users.
 */
public class TestApi {

    private final TestRestTemplate rest;

    public TestApi(TestRestTemplate rest) {
        this.rest = rest;
    }

    public record Actor(String token, UUID userId, UUID organizationId, String email) {
    }

    @SuppressWarnings("unchecked")
    public Actor register(String orgName, String orgType) {
        String email = "u-" + UUID.randomUUID() + "@test.local";
        Map<String, Object> body = Map.of("email", email, "password", "password123", "fullName", "Test User",
                "organizationName", orgName, "organizationType", orgType);
        Map<String, Object> res = rest.postForObject("/api/auth/register", body, Map.class);
        Map<String, Object> user = (Map<String, Object>) res.get("user");
        Map<String, Object> org = (Map<String, Object>) res.get("organization");
        return new Actor((String) res.get("token"), UUID.fromString((String) user.get("id")),
                UUID.fromString((String) org.get("id")), email);
    }

    public ResponseEntity<Map> call(Actor actor, HttpMethod method, String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    public ResponseEntity<String> raw(Actor actor, HttpMethod method, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.token());
        return rest.exchange(path, method, new HttpEntity<>(headers), String.class);
    }

    public UUID create(Actor actor, String path, Object body) {
        ResponseEntity<Map> res = call(actor, HttpMethod.POST, path, body);
        if (!res.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("POST " + path + " -> " + res.getStatusCode() + " " + res.getBody());
        }
        return UUID.fromString((String) res.getBody().get("id"));
    }

    public UUID uploadEvidence(Actor actor, UUID workstreamId, String filename, byte[] content, String documentType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        form.add("documentType", documentType);
        ResponseEntity<Map> res = rest.exchange("/api/workstreams/" + workstreamId + "/evidence", HttpMethod.POST,
                new HttpEntity<>(form, headers), Map.class);
        if (!res.getStatusCode().is2xxSuccessful()) {
            throw new AssertionError("upload -> " + res.getStatusCode() + " " + res.getBody());
        }
        return UUID.fromString((String) res.getBody().get("id"));
    }
}
