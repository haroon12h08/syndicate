package com.syndicate.regulatory.engine;

import com.syndicate.regulatory.engine.TransactionLifecycleState;
import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegulatoryEngineIT extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate rest;

    private TestApi api;
    private TransactionFixture tx;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "LEGAL_DUE_DILIGENCE");
    }

    private HttpHeaders authHeaders(Actor actor) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(actor.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @Test
    @DisplayName("End-to-End Regulatory Engine: Pinned snapshot, Control evaluations, Governance four-eyes author check, Exception override, and Lifecycle state machine transition")
    void testRegulatoryEngineAndLifecycleFlow() {
        Actor issuer = tx.addMember(api, "ISSUER_ADMIN");
        HttpHeaders leadHeaders = authHeaders(tx.lead());
        HttpHeaders issuerHeaders = authHeaders(issuer);
        UUID txnId = tx.transactionId();

        // 1. Check Initial Pinned Snapshot
        ResponseEntity<Map> snapshotResp = rest.exchange(
                "/api/transactions/" + txnId + "/regulatory-snapshot",
                HttpMethod.GET,
                new HttpEntity<>(leadHeaders),
                Map.class
        );
        assertThat(snapshotResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(snapshotResp.getBody()).isNotNull();
        assertThat((Boolean) snapshotResp.getBody().get("isActive")).isTrue();

        // 2. Check Drift Detection
        ResponseEntity<Map> driftResp = rest.exchange(
                "/api/transactions/" + txnId + "/regulatory-snapshot/drift",
                HttpMethod.GET,
                new HttpEntity<>(leadHeaders),
                Map.class
        );
        assertThat(driftResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Evaluate Regulatory Controls
        ResponseEntity<List> evalResp = rest.exchange(
                "/api/transactions/" + txnId + "/regulatory/evaluate",
                HttpMethod.POST,
                new HttpEntity<>(leadHeaders),
                List.class
        );
        assertThat(evalResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(evalResp.getBody()).isNotEmpty();

        // Verify Invariant 18 wording boundary on evaluation items
        List<Map<String, Object>> evaluations = evalResp.getBody();
        for (Map<String, Object> item : evaluations) {
            String explanation = (String) item.get("explanation");
            assertThat(explanation).startsWith("Based on configured rule");
            assertThat(explanation.toLowerCase()).doesNotContain("sebi will approve");
            assertThat(explanation.toLowerCase()).doesNotContain("compliant with sebi");
        }

        // 4. Multi-Eye Governance: Lead Banker authors new requirement
        Map<String, Object> reqPayload = new HashMap<>();
        reqPayload.put("code", "REG_SEBI_PROMOTER_TRACK_2026");
        reqPayload.put("authority", "SEBI");
        reqPayload.put("jurisdiction", "INDIA");
        reqPayload.put("title", "Promoter Track Record Minimum Standard");
        reqPayload.put("description", "Requires 3-year proven operating track record");
        reqPayload.put("transactionType", "SME_IPO");
        reqPayload.put("issueType", "ALL");
        reqPayload.put("severity", "HIGH");
        reqPayload.put("citation", "SEBI ICDR Regulations Schedule VI §4");
        reqPayload.put("sourceUrl", "https://www.sebi.gov.in/legal/regulations/icdr-2026.html");
        reqPayload.put("effectiveFrom", LocalDate.now().toString());

        Map<String, Object> controlPayload = new HashMap<>();
        controlPayload.put("code", "CTRL_PROMOTER_TRACK_YEARS");
        controlPayload.put("name", "3-Year Operating History Check");
        controlPayload.put("description", "Verifies operating track record");
        controlPayload.put("controlType", "DETERMINISTIC_EXPRESSION");
        controlPayload.put("severity", "HIGH");
        controlPayload.put("orderIndex", 1);
        reqPayload.put("controls", List.of(controlPayload));

        Map<String, Object> testCasePayload = new HashMap<>();
        testCasePayload.put("name", "Positive 3-year track");
        testCasePayload.put("fixtureInputs", "{\"years\": 3}");
        testCasePayload.put("expectedStatus", "PASS");
        reqPayload.put("testCases", List.of(testCasePayload));

        ResponseEntity<Map> createResp = rest.exchange(
                "/api/regulatory/requirements",
                HttpMethod.POST,
                new HttpEntity<>(reqPayload, leadHeaders),
                Map.class
        );
        assertThat(createResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String versionId = (String) createResp.getBody().get("id");

        // Run test cases
        ResponseEntity<List> testResp = rest.exchange(
                "/api/regulatory/requirements/" + versionId + "/test",
                HttpMethod.POST,
                new HttpEntity<>(leadHeaders),
                List.class
        );
        assertThat(testResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // Submit for approval
        ResponseEntity<Map> submitResp = rest.exchange(
                "/api/regulatory/requirements/" + versionId + "/submit",
                HttpMethod.POST,
                new HttpEntity<>(leadHeaders),
                Map.class
        );
        assertThat(submitResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) submitResp.getBody().get("status")).isEqualTo("PENDING_APPROVAL");

        // Four-eyes test: Author (Lead Banker) tries to approve own requirement -> Forbidden 403!
        ResponseEntity<Map> selfApproveResp = rest.exchange(
                "/api/regulatory/requirements/" + versionId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(leadHeaders),
                Map.class
        );
        assertThat(selfApproveResp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Independent Approver (Issuer Admin) approves requirement -> 200 OK!
        ResponseEntity<Map> peerApproveResp = rest.exchange(
                "/api/regulatory/requirements/" + versionId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(issuerHeaders),
                Map.class
        );
        assertThat(peerApproveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) peerApproveResp.getBody().get("status")).isEqualTo("ACTIVE");

        // 5. Exception Management & Lifecycle State Machine
        // Find a control to request an exception on
        String targetControlId = (String) evaluations.get(0).get("controlId");

        Map<String, Object> exceptionPayload = new HashMap<>();
        exceptionPayload.put("controlId", targetControlId);
        exceptionPayload.put("reason", "Board resolution passed guaranteeing supplementary lock-in");
        exceptionPayload.put("affectedState", "INTERNAL_APPROVAL");
        exceptionPayload.put("riskClassification", "MEDIUM");
        exceptionPayload.put("expiryDate", LocalDate.now().plusMonths(3).toString());
        exceptionPayload.put("mitigationPlan", "Promoter escrow arrangement executed with lead banker");

        ResponseEntity<Map> excReqResp = rest.exchange(
                "/api/transactions/" + txnId + "/regulatory-exceptions",
                HttpMethod.POST,
                new HttpEntity<>(exceptionPayload, leadHeaders),
                Map.class
        );
        assertThat(excReqResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String exceptionId = (String) excReqResp.getBody().get("id");
        assertThat((String) excReqResp.getBody().get("status")).isEqualTo("REQUESTED");

        // Approve exception using different authorized actor
        ResponseEntity<Map> excApproveResp = rest.exchange(
                "/api/regulatory-exceptions/" + exceptionId + "/approve",
                HttpMethod.POST,
                new HttpEntity<>(issuerHeaders),
                Map.class
        );
        assertThat(excApproveResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((String) excApproveResp.getBody().get("status")).isEqualTo("APPROVED");

        // 6. Transition Lifecycle State Machine
        Map<String, Object> transitionPayload = new HashMap<>();
        transitionPayload.put("targetState", "DUE_DILIGENCE");
        transitionPayload.put("reason", "Proceeding with formal due diligence workstream");
        transitionPayload.put("forceWithException", true);

        ResponseEntity<Map> transResp = rest.exchange(
                "/api/transactions/" + txnId + "/lifecycle/transition",
                HttpMethod.POST,
                new HttpEntity<>(transitionPayload, leadHeaders),
                Map.class
        );
        assertThat(transResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat((Boolean) transResp.getBody().get("allowed")).isTrue();
        assertThat((String) transResp.getBody().get("toState")).isEqualTo("DUE_DILIGENCE");

        // Verify history endpoint
        ResponseEntity<String> historyResp = rest.exchange(
                "/api/transactions/" + txnId + "/lifecycle/history",
                HttpMethod.GET,
                new HttpEntity<>(leadHeaders),
                String.class
        );
        System.err.println("=== HISTORY RESP STATUS: " + historyResp.getStatusCode() + " BODY: " + historyResp.getBody());
        assertThat(historyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(historyResp.getBody()).contains("DUE_DILIGENCE");
    }
}
