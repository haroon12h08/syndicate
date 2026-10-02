package com.syndicate.security;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What anyone on the internet can reach, and what they cannot push through it. */
class PublicSurfaceIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;

    private ResponseEntity<Map> upload(TestApi api, TransactionFixture tx, String fileName, byte[] bytes) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tx.lead().token());
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });
        form.add("documentType", "OTHER");
        return rest.exchange("/api/workstreams/" + tx.workstreamId() + "/evidence", HttpMethod.POST,
                new HttpEntity<>(form, headers), Map.class);
    }

    @Test
    void uploadsAreCheckedByTheirContentNotTheirName() {
        TestApi api = new TestApi(rest);
        TransactionFixture tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");

        // an executable renamed to .pdf is refused
        byte[] elf = new byte[]{0x7F, 'E', 'L', 'F', 2, 1, 1, 0, 0, 0};
        ResponseEntity<Map> disguised = upload(api, tx, "statements.pdf", elf);
        assertThat(disguised.getStatusCode().value()).isEqualTo(400);
        assertThat(String.valueOf(disguised.getBody().get("message"))).contains("not really a PDF");

        // an unsupported kind is refused by extension
        assertThat(upload(api, tx, "payload.exe", "MZ".getBytes(StandardCharsets.UTF_8))
                .getStatusCode().value()).isEqualTo(400);

        // a real PDF is accepted
        byte[] pdf = ("%PDF-1.4\n1 0 obj\n<< >>\nendobj\ntrailer\n<< >>\n%%EOF " + UUID.randomUUID())
                .getBytes(StandardCharsets.UTF_8);
        assertThat(upload(api, tx, "statements.pdf", pdf).getStatusCode().value()).isEqualTo(201);
    }

    @org.springframework.boot.test.web.server.LocalServerPort int port;

    @Test
    void repeatedSignInAttemptsAreThrottled() throws Exception {
        // a plain client, because a RestTemplate will not replay a request that was refused
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        String body = "{\"email\":\"nobody-" + UUID.randomUUID()
                + "@test.local\",\"password\":\"wrong-password\"}";
        int lastStatus = 0;
        for (int attempt = 0; attempt < 12; attempt++) {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("http://localhost:" + port + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(body))
                    .build();
            lastStatus = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode();
            if (lastStatus == 429) {
                break;
            }
        }
        assertThat(lastStatus).as("sign-in should be throttled before 12 attempts").isEqualTo(429);
    }

    @Test
    void securityHeadersAreSetOnEveryResponse() {
        HttpHeaders headers = rest.getForEntity("/api/health", Map.class).getHeaders();
        assertThat(headers.getFirst("Content-Security-Policy")).contains("default-src 'self'");
        assertThat(headers.getFirst("X-Frame-Options")).isEqualTo("DENY");
        assertThat(headers.getFirst("Referrer-Policy")).isEqualTo("same-origin");
    }
}
