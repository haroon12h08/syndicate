package com.syndicate.filing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec §18, §37 & Build Plan Ticket F4:
 * Integration test verifying resubmission tracking, version diffing,
 * formal statement of changes generation, and filing package bundling.
 */
class ResubmissionIT extends IntegrationTestBase {

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper objectMapper;

    TestApi api;
    TransactionFixture tx;
    Actor banker;
    Actor issuerAdmin;
    Actor auditor;

    Map capitalFact;
    Map docV1;
    String disclosureId;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "CAPITAL_STRUCTURE");
        banker = tx.lead();
        issuerAdmin = tx.addMember(api, "ISSUER_ADMIN");
        auditor = tx.addMember(api, "AUDITOR");

        // Satisfy compile readiness rules
        capitalFact = establish("issue.post_issue_paid_up_capital", "Post-issue paid-up capital", "180000000");
        establish("issuer.ebitda", "EBITDA", "64000000", "FY2026");
        establish("issuer.ebitda", "EBITDA", "51000000", "FY2025");
        establish("issuer.ebitda", "EBITDA", "43000000", "FY2024");
        establish("issuer.licence_expiry", "Factory licence expiry", "4102444800000");

        tx.settleBlockingQuestions(api, api.uploadEvidence(banker, tx.workstreamId(), "diligence-file.txt",
                ("diligence " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER"));

        UUID discId = api.create(banker, "/api/transactions/" + tx.transactionId() + "/disclosures",
                Map.of("sectionCode", "CAPITAL_STRUCTURE", "title", "Capital structure",
                        "bodyTemplate", "Post-issue paid-up capital stands at INR {{fact:issue.post_issue_paid_up_capital}}."));
        disclosureId = discId.toString();

        Map compiledV1 = api.call(banker, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/drhp/compile?mode=FINAL_FILING", null).getBody();
        assertThat(compiledV1.get("compiled")).isEqualTo(true);
        docV1 = (Map) compiledV1.get("document");
    }

    private Map establish(String factKey, String label, String value) {
        return establish(factKey, label, value, null);
    }

    private Map establish(String factKey, String label, String value, String period) {
        UUID evidenceId = api.uploadEvidence(banker, tx.workstreamId(), label + ".txt",
                (label + value + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        Map<String, Object> body = new HashMap<>(Map.of("factKey", factKey, "label", label, "value", value,
                "unit", "INR"));
        if (period != null) {
            body.put("period", period);
        }
        Map fact = api.call(banker, HttpMethod.POST, "/api/workstreams/" + tx.workstreamId() + "/facts", body).getBody();
        api.call(banker, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/evidence-links",
                Map.of("evidenceId", evidenceId));
        return api.call(auditor, HttpMethod.POST, "/api/facts/" + fact.get("id") + "/verify", null).getBody();
    }

    @Test
    @SuppressWarnings("unchecked")
    void versionDiffAndStatementOfChangesReflectMutationsAndObservations() throws Exception {
        // Step 1: Verify DRHP v1 diff against itself has zero differences
        Map v1Diff = api.call(banker, HttpMethod.GET,
                "/api/drhp/versions/" + docV1.get("id") + "/diff", null).getBody();
        Map statsV1 = (Map) v1Diff.get("stats");
        assertThat(statsV1.get("factsSuperseded")).isEqualTo(0);
        assertThat(statsV1.get("factsAdded")).isEqualTo(0);
        assertThat(statsV1.get("disclosuresModified")).isEqualTo(0);

        // Step 2: Mutate a fact with recorded human justification
        String changeReason = "Resized per Board resolution dated 12 Oct 2026";
        Map updatedFact = api.call(banker, HttpMethod.PUT, "/api/facts/" + capitalFact.get("id"),
                Map.of("factKey", "issue.post_issue_paid_up_capital",
                        "label", "Post-issue paid-up capital",
                        "value", "195000000",
                        "unit", "INR",
                        "reason", changeReason)).getBody();

        // Link fresh supportive evidence to satisfy readiness gate
        UUID newEvidenceId = api.uploadEvidence(banker, tx.workstreamId(), "board-resolution.txt",
                ("board-resolution " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), "OTHER");
        api.call(banker, HttpMethod.POST, "/api/facts/" + updatedFact.get("id") + "/evidence-links",
                Map.of("evidenceId", newEvidenceId));

        // Re-verify the mutated fact (auditor four-eyes verification)
        api.call(auditor, HttpMethod.POST, "/api/facts/" + updatedFact.get("id") + "/verify", null);

        // Re-review the disclosure (which was marked stale by mutation) and clear stale status
        api.call(banker, HttpMethod.PUT, "/api/disclosures/" + disclosureId,
                Map.of("sectionCode", "CAPITAL_STRUCTURE",
                        "title", "Capital structure",
                        "bodyTemplate", "Post-issue paid-up capital stands at INR {{fact:issue.post_issue_paid_up_capital}}.",
                        "status", "DRAFT"));

        // Step 3: Record and respond to a regulatory observation
        Map obs = api.call(banker, HttpMethod.POST, "/api/transactions/" + tx.transactionId() + "/observations",
                Map.of("authority", "SEBI",
                        "reference", "SEBI/CFD/2026/091",
                        "receivedDate", LocalDate.now().toString(),
                        "observation", "Clarify final post-issue paid up capital breakdown.",
                        "sectionCode", "CAPITAL_STRUCTURE")).getBody();
        String obsId = (String) obs.get("id");
        Map resp = api.call(banker, HttpMethod.PUT, "/api/observations/" + obsId + "/response",
                Map.of("response", "Capital structure table updated in paragraph 4.2 reflected in v2.",
                        "disclosureIds", List.of(UUID.fromString(disclosureId)))).getBody();
        assertThat(resp.get("status")).isEqualTo("RESPONSE_DRAFTED");

        Map approvedObs = api.call(auditor, HttpMethod.POST, "/api/observations/" + obsId + "/response/approve", null).getBody();
        assertThat(approvedObs.get("status")).isEqualTo("RESPONSE_APPROVED");

        // Step 4: Compile DRHP v2
        Map compiledV2 = api.call(banker, HttpMethod.POST,
                "/api/transactions/" + tx.transactionId() + "/drhp/compile?mode=FINAL_FILING", null).getBody();
        assertThat(compiledV2.get("compiled")).isEqualTo(true);
        Map docV2 = (Map) compiledV2.get("document");
        assertThat(docV2.get("version")).isEqualTo(2);

        // Step 5: Test version diff endpoint
        Map diff = api.call(banker, HttpMethod.GET,
                "/api/drhp/versions/" + docV2.get("id") + "/diff", null).getBody();

        Map stats = (Map) diff.get("stats");
        assertThat(stats.get("factsSuperseded")).isEqualTo(1);
        assertThat(stats.get("disclosuresModified")).isEqualTo(1);
        assertThat(stats.get("observationsAddressed")).isEqualTo(1);

        Map baseDoc = (Map) diff.get("baseDocument");
        Map targetDoc = (Map) diff.get("targetDocument");
        assertThat(baseDoc.get("version")).isEqualTo(1);
        assertThat(targetDoc.get("version")).isEqualTo(2);

        List<Map> factDiffs = (List<Map>) diff.get("facts");
        Map changedFactDiff = factDiffs.stream()
                .filter(f -> "issue.post_issue_paid_up_capital".equals(f.get("factKey")))
                .findFirst()
                .orElseThrow();
        assertThat(changedFactDiff.get("changeType")).isEqualTo("SUPERSEDED_VALUE_CHANGED");
        assertThat(changedFactDiff.get("baseValue")).isEqualTo("180000000");
        assertThat(changedFactDiff.get("targetValue")).isEqualTo("195000000");
        assertThat(changedFactDiff.get("changeReason")).isEqualTo(changeReason);
        assertThat(changedFactDiff.get("correctedBy")).isNotNull();

        List<Map> discDiffs = (List<Map>) diff.get("disclosures");
        Map changedDisc = discDiffs.stream()
                .filter(d -> "CAPITAL_STRUCTURE".equals(d.get("sectionCode")))
                .findFirst()
                .orElseThrow();
        assertThat(changedDisc.get("changeType")).isEqualTo("MODIFIED");
        assertThat((String) changedDisc.get("baseProse")).contains("180000000");
        assertThat((String) changedDisc.get("targetProse")).contains("195000000");

        List<Map> obsList = (List<Map>) diff.get("addressedObservations");
        assertThat(obsList).hasSize(1);
        assertThat(obsList.get(0).get("reference")).isEqualTo("SEBI/CFD/2026/091");
        assertThat(obsList.get(0).get("status")).isEqualTo("RESPONSE_APPROVED");

        // Step 6: Test Statement of Changes plain text endpoint
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(banker.token());
        ResponseEntity<String> stmtResp = rest.exchange(
                "/api/drhp/versions/" + docV2.get("id") + "/statement-of-changes",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
        assertThat(stmtResp.getStatusCode().is2xxSuccessful()).isTrue();
        String text = stmtResp.getBody();
        assertThat(text).isNotNull();
        assertThat(text).contains("STATEMENT OF CHANGES & RESUBMISSION EXPLANATION");
        assertThat(text).contains("Base Filing: DRHP v1");
        assertThat(text).contains("Resubmission: DRHP v2");
        assertThat(text).contains("Facts Superseded / Corrected: 1");
        assertThat(text).contains("Disclosures Modified:         1");
        assertThat(text).contains("Reason for Change: " + changeReason);
        assertThat(text).contains("SEBI/CFD/2026/091");

        // Step 7: Approve v2 and assemble filing package v2
        api.call(banker, HttpMethod.POST, "/api/drhp/versions/" + docV2.get("id") + "/approvals", null);
        api.call(issuerAdmin, HttpMethod.POST, "/api/drhp/versions/" + docV2.get("id") + "/approvals", null);

        Map assembled = api.call(banker, HttpMethod.POST,
                "/api/drhp/versions/" + docV2.get("id") + "/filing-package", null).getBody();
        assertThat(assembled.get("readyToAssemble")).isEqualTo(true);

        // Step 8: Download package ZIP and assert statement-of-changes entries
        byte[] zipBytes = rest.exchange(
                "/api/drhp/versions/" + docV2.get("id") + "/filing-package",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class).getBody();

        List<String> entryNames = new ArrayList<>();
        String zipStatementText = null;
        String zipStatementJson = null;

        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                entryNames.add(entry.getName());
                if ("statement-of-changes.txt".equals(entry.getName())) {
                    zipStatementText = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } else if ("statement-of-changes.json".equals(entry.getName())) {
                    zipStatementJson = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }

        assertThat(entryNames).contains(
                "document.json",
                "document.pdf",
                "document.docx",
                "provenance-manifest.json",
                "evidence-index.csv",
                "approval-certificate.txt",
                "statement-of-changes.txt",
                "statement-of-changes.json",
                "README.txt"
        );

        assertThat(zipStatementText).isNotNull();
        assertThat(zipStatementText).contains("STATEMENT OF CHANGES & RESUBMISSION EXPLANATION");
        assertThat(zipStatementText).contains(changeReason);

        assertThat(zipStatementJson).isNotNull();
        assertThat(zipStatementJson).contains("SUPERSEDED_VALUE_CHANGED");
        assertThat(zipStatementJson).contains("195000000");
    }
}
