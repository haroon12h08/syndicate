package com.syndicate.invariants;

import com.syndicate.ai.drafting.dto.DraftDisclosureRequest;
import com.syndicate.ai.drafting.dto.DraftObservationResponseRequest;
import com.syndicate.ai.llm.MockLlmClient;
import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase G Intelligence Layer Expansion Integration Tests (Tickets G3, G4, G5).
 * Tests:
 * 1. Transaction Search (G3): searches across Facts, Evidence, Candidates, Disclosures, Questions, Conflicts.
 * 2. Suggestions Engine (G4): pre-conflict detection, missing evidence detection, diligence gap, human accept/dismiss.
 * 3. AI Drafting Assist (G5): disclosure draft assist with valid placeholders and regulatory observation response assist.
 */
class AiIntelligenceLayerIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockLlmClient mockLlmClient;

    TestApi api;
    TransactionFixture tx;
    Actor lead;
    Actor auditor;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
        lead = tx.lead();
        auditor = tx.addMember(api, "AUDITOR");
    }

    @AfterEach
    void tearDown() {
        mockLlmClient.clearCustomHandler();
    }

    private void awaitExtractionSettled(UUID id) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            String status = jdbc.queryForObject("SELECT processing_status FROM evidence WHERE id = ?", String.class, id);
            if (!"PENDING".equals(status) && !"PROCESSING".equals(status)) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        throw new AssertionError("extraction did not settle for " + id);
    }

    @Test
    void transactionSearchFindsMultiEntityResultsWithProvenanceAndBadges() {
        // 1. Create a fact
        ResponseEntity<Map> factResp = api.call(auditor, HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts", Map.of(
                "label", "Paid-Up Share Capital",
                "value", "10000000",
                "unit", "INR",
                "period", "FY2026"
        ));
        assertThat(factResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 2. Upload text evidence
        byte[] docBytes = "Audited Balance Sheet FY2026 with total assets and paid-up capital".getBytes(StandardCharsets.UTF_8);
        UUID evidenceId = api.uploadEvidence(auditor, tx.workstreamId(), "balance_sheet_fy26.txt", docBytes, "AUDITED_FINANCIAL_STATEMENT");
        awaitExtractionSettled(evidenceId);

        // 3. Create a DRHP disclosure
        ResponseEntity<Map> discResp = api.call(lead, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/disclosures", Map.of(
                "sectionCode", "CAPITAL_STRUCTURE",
                "title", "Capital Structure Overview",
                "bodyTemplate", "The authorized capital is 20000000 and paid-up share capital is {{fact:issuer.paid_up_capital@FY2026}}."
        ));
        assertThat(discResp.getStatusCode().is2xxSuccessful()).isTrue();

        // 4. Execute Transaction Search (q="Capital")
        ResponseEntity<Map> searchResp = api.call(auditor, HttpMethod.GET,
                "/api/transactions/" + tx.transactionId() + "/search?q=Capital", null);
        assertThat(searchResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> body = searchResp.getBody();
        assertThat(body).isNotNull();

        List<?> results = (List<?>) body.get("results");
        assertThat(results).isNotEmpty();

        // Verify result items have type, state badge, provenance, deepLink
        boolean foundFact = false;
        boolean foundDisclosure = false;
        for (Object itemObj : results) {
            Map<?, ?> item = (Map<?, ?>) itemObj;
            String entityType = (String) item.get("entityType");
            assertThat(item.get("state")).isNotNull();
            assertThat(item.get("provenanceSummary")).isNotNull();
            assertThat(item.get("deepLinkPath")).isNotNull();

            if ("FACT".equals(entityType)) foundFact = true;
            if ("DISCLOSURE".equals(entityType)) foundDisclosure = true;
        }
        assertThat(foundFact).isTrue();
        assertThat(foundDisclosure).isTrue();
    }

    @Test
    void aiSuggestionsDetectsMissingEvidenceAndSupportsHumanReviewLoop() {
        // Initially, the transaction lacks Board Resolution and Material Contracts.
        // Call generate suggestions
        ResponseEntity<Map> genResp = api.call(lead, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/suggestions/generate", null);
        assertThat(genResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> genBody = genResp.getBody();
        assertThat(genBody).isNotNull();

        List<?> suggestions = (List<?>) genBody.get("suggestions");
        assertThat(suggestions).isNotEmpty();

        // Find a missing evidence suggestion
        Map<?, ?> missingEv = suggestions.stream()
                .map(s -> (Map<?, ?>) s)
                .filter(s -> "MISSING_EVIDENCE".equals(s.get("suggestionType")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected MISSING_EVIDENCE suggestion"));

        UUID suggestionId = UUID.fromString((String) missingEv.get("id"));
        assertThat(missingEv.get("severity")).isIn("BLOCKING", "WARNING");
        assertThat(missingEv.get("status")).isEqualTo("PENDING");

        // An ai_artifact record must have been created
        assertThat(missingEv.get("aiArtifactId")).isNotNull();

        // Human dismisses the suggestion
        ResponseEntity<Map> dismissResp = api.call(lead, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/suggestions/" + suggestionId + "/dismiss",
                Map.of("reason", "Board resolution obtained via physical registry"));
        assertThat(dismissResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> dismissed = dismissResp.getBody();
        assertThat(dismissed.get("status")).isEqualTo("DISMISSED");
        assertThat(dismissed.get("dismissReason")).isEqualTo("Board resolution obtained via physical registry");
    }

    @Test
    void aiDraftingAssistGeneratesDisclosuresWithValidPlaceholdersAndObservations() {
        // 1. Create and verify a fact
        ResponseEntity<Map> factResp = api.call(auditor, HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts", Map.of(
                "label", "Total Revenue",
                "value", "150.00 Cr",
                "unit", "INR",
                "period", "FY2026"
        ));
        UUID factId = UUID.fromString((String) factResp.getBody().get("id"));
        api.call(lead, HttpMethod.POST, "/api/facts/" + factId + "/verify", Map.of());

        // 2. Draft disclosure via AI assist
        ResponseEntity<Map> draftResp = api.call(lead, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/drhp/disclosures/draft-ai",
                new DraftDisclosureRequest("FINANCIAL_INFORMATION", "Financial Overview", "Draft revenue disclosure"));
        assertThat(draftResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> draft = draftResp.getBody();
        assertThat(draft).isNotNull();
        String bodyTemplate = (String) draft.get("bodyTemplate");
        assertThat(bodyTemplate).contains("{{fact:issuer.revenue@FY2026}}");
        List<?> citedKeys = (List<?>) draft.get("citedFactKeys");
        assertThat(citedKeys).asList().contains("issuer.revenue");
        assertThat(draft.get("aiArtifactId")).isNotNull();

        // 3. Record a Regulatory Observation
        ResponseEntity<Map> obsResp = api.call(lead, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/observations", Map.of(
                "authority", "SEBI",
                "reference", "SEBI/CFD/2026/042",
                "receivedDate", LocalDate.now().toString(),
                "observation", "Explain revenue growth and inventory movements in FY2026",
                "sectionCode", "FINANCIAL_INFORMATION",
                "severity", "HIGH"
        ));
        UUID obsId = UUID.fromString((String) obsResp.getBody().get("id"));

        // 4. Draft observation response via AI assist
        ResponseEntity<Map> obsDraftResp = api.call(lead, HttpMethod.POST,
                "/api/observations/" + obsId + "/draft-ai",
                new DraftObservationResponseRequest("Draft detailed response citing verified revenue"));
        assertThat(obsDraftResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> obsDraft = obsDraftResp.getBody();
        assertThat(obsDraft).isNotNull();
        assertThat((String) obsDraft.get("suggestedResponse")).isNotEmpty();
        assertThat((List<?>) obsDraft.get("suggestedFactIds")).isNotEmpty();
        assertThat(obsDraft.get("aiArtifactId")).isNotNull();

        // Zero AI Authority: Observation response remains unapproved/unsubmitted until human responds
        String obsStatus = jdbc.queryForObject("SELECT status FROM regulatory_observations WHERE id = ?", String.class, obsId);
        assertThat(obsStatus).isEqualTo("OPEN");
    }
}
