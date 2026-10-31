package com.syndicate.captable;

import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TestApi.Actor;
import com.syndicate.support.TransactionFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class CapTableAndRegistersIT extends IntegrationTestBase {

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
    @DisplayName("End-to-end Cap Table: create security classes, holders, actions, IPO params and verify 2025 amended lock-ins")
    void testCapTableLifecycle() {
        HttpHeaders headers = authHeaders(tx.lead());

        // 1. Create Equity security class
        Map<String, Object> secReq = new HashMap<>();
        secReq.put("name", "Equity Shares");
        secReq.put("securityType", "EQUITY");
        secReq.put("currency", "INR");
        secReq.put("faceValue", 10.0);
        secReq.put("votingRightsPerShare", 1.0);
        secReq.put("authorizedShares", 10000000);
        secReq.put("convertible", false);

        ResponseEntity<Map> secResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/captable/security-classes",
                HttpMethod.POST,
                new HttpEntity<>(secReq, headers),
                Map.class
        );
        assertThat(secResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String secClassId = (String) secResp.getBody().get("id");
        assertThat(secClassId).isNotNull();

        // 2. Create Promoter Holder
        Map<String, Object> holderReq = new HashMap<>();
        holderReq.put("legalName", "Haroon Trinity");
        holderReq.put("category", "PROMOTER");
        holderReq.put("pan", "ABCDE1234F");
        holderReq.put("dematAccountNo", "1208160012345678");

        ResponseEntity<Map> holderResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/captable/holders",
                HttpMethod.POST,
                new HttpEntity<>(holderReq, headers),
                Map.class
        );
        assertThat(holderResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String holderId = (String) holderResp.getBody().get("id");
        assertThat(holderId).isNotNull();

        // 3. Record Initial Allotment (5,000,000 shares)
        Map<String, Object> txReq = new HashMap<>();
        txReq.put("securityClassId", secClassId);
        txReq.put("eventType", "INCORPORATION");
        txReq.put("eventDate", "2024-01-01");
        txReq.put("destHolderId", holderId);
        txReq.put("shareCount", 5000000);
        txReq.put("pricePerShare", 10.0);
        txReq.put("faceValueAtEvent", 10.0);
        txReq.put("considerationType", "CASH_BANK");
        txReq.put("notes", "Initial founder allotment");

        ResponseEntity<Map> txResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/captable/transactions",
                HttpMethod.POST,
                new HttpEntity<>(txReq, headers),
                Map.class
        );
        assertThat(txResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 4. Update IPO issue parameters: Fresh Issue 2,000,000 shares, OFS 1,000,000 shares
        Map<String, Object> ipoReq = new HashMap<>();
        ipoReq.put("freshIssueShares", 2000000);
        ipoReq.put("ofsShares", 1000000);
        ipoReq.put("priceBandLower", 120.0);
        ipoReq.put("priceBandUpper", 130.0);
        ipoReq.put("expectedAllotmentDate", "2026-12-01");

        ResponseEntity<Map> ipoResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/captable/ipo-parameters",
                HttpMethod.PUT,
                new HttpEntity<>(ipoReq, headers),
                Map.class
        );
        assertThat(ipoResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 5. Query Cap Table Summary
        ResponseEntity<Map> summaryResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/captable",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        assertThat(summaryResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> summary = summaryResp.getBody();
        assertThat(summary).isNotNull();
        // Post issue shares: 5M basic + 2M fresh = 7M
        assertThat(new BigDecimal(summary.get("postIssueShares").toString())).isEqualByComparingTo(new BigDecimal("7000000"));
        assertThat((Boolean) summary.get("isMpcSatisfied")).isTrue();

        // 6. Recalculate and persist 2025 tiered lock-ins
        Map<String, Object> lockInReq = Map.of("allotmentDate", "2026-12-01");
        ResponseEntity<List> lockInResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/captable/lock-ins/recalculate",
                HttpMethod.POST,
                new HttpEntity<>(lockInReq, headers),
                List.class
        );
        assertThat(lockInResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List lockIns = lockInResp.getBody();
        assertThat(lockIns).isNotEmpty();
    }

    @Test
    @DisplayName("End-to-end Domain Registers: RPT, Litigation, Material Contracts & Blocker Integration")
    void testRegistersAndBlockers() {
        HttpHeaders headers = authHeaders(tx.lead());

        // 1. Record Related Party
        Map<String, Object> partyReq = new HashMap<>();
        partyReq.put("name", "Trinity Tech Solutions");
        partyReq.put("partyType", "BODY_CORPORATE");
        partyReq.put("pan", "ABCDE5678G");
        partyReq.put("relationshipNature", "PROMOTER");
        partyReq.put("effectiveFrom", "2021-01-01");

        ResponseEntity<Map> partyResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/rpt/parties",
                HttpMethod.POST,
                new HttpEntity<>(partyReq, headers),
                Map.class
        );
        assertThat(partyResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        String partyId = (String) partyResp.getBody().get("id");

        // 2. Record RPT Transaction
        Map<String, Object> rptReq = new HashMap<>();
        rptReq.put("relatedPartyId", partyId);
        rptReq.put("financialYear", "FY2025-26");
        rptReq.put("transactionType", "PURCHASE_OF_GOODS");
        rptReq.put("amount", 50000000.0); // ₹5 Crore
        rptReq.put("currency", "INR");
        rptReq.put("inOrdinaryCourse", true);
        rptReq.put("isArmsLength", true);
        rptReq.put("armLengthBasis", "Benchmarked against third-party quotation");
        rptReq.put("auditCommitteeApproval", "OMNIBUS");

        ResponseEntity<Map> rptResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/rpt/transactions",
                HttpMethod.POST,
                new HttpEntity<>(rptReq, headers),
                Map.class
        );
        assertThat(rptResp.getStatusCode()).isEqualTo(HttpStatus.OK);

        // 3. Record Pending Criminal Litigation Case
        Map<String, Object> litReq = new HashMap<>();
        litReq.put("workstreamId", tx.workstreamId().toString());
        litReq.put("caseNumber", "CC/2024/999");
        litReq.put("courtForum", "Metropolitan Magistrate Court");
        litReq.put("jurisdictionCity", "Mumbai");
        litReq.put("caseTitle", "State vs. Key Promoter");
        litReq.put("category", "CRIMINAL_PROCEEDING");
        litReq.put("entityType", "PROMOTER");
        litReq.put("entityName", "Haroon Trinity");
        litReq.put("partyRole", "RESPONDENT_DEFENDANT");
        litReq.put("oppositeParty", "State");
        litReq.put("claimedAmount", 0.0);
        litReq.put("potentialImpact", "REPUTATIONAL");
        litReq.put("caseSummary", "Alleged delayed statutory return");
        litReq.put("status", "PENDING");

        ResponseEntity<Map> litResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/litigation/cases",
                HttpMethod.POST,
                new HttpEntity<>(litReq, headers),
                Map.class
        );
        assertThat(litResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Boolean isMaterial = (Boolean) litResp.getBody().get("isMaterial");
        assertThat(isMaterial).isTrue();

        // 4. Record Material Contract with Change of Control without consent
        Map<String, Object> contractReq = new HashMap<>();
        contractReq.put("workstreamId", tx.workstreamId().toString());
        contractReq.put("contractReference", "KVC-2024-01");
        contractReq.put("title", "Exclusive Raw Material Supply Agreement");
        contractReq.put("contractCategory", "KEY_VENDOR_CONTRACT");
        contractReq.put("counterpartyName", "MegaSupplier Ltd");
        contractReq.put("executionDate", "2024-02-01");
        contractReq.put("effectiveDate", "2024-02-01");
        contractReq.put("supplyPercentage", 18.5); // > 10% screening indicator
        contractReq.put("hasChangeOfControl", true);
        contractReq.put("changeOfControlConsentObtained", false); // Pending consent!
        contractReq.put("hasExclusivity", true);

        ResponseEntity<Map> contractResp = rest.exchange(
                "/api/v1/transactions/" + tx.transactionId() + "/contracts",
                HttpMethod.POST,
                new HttpEntity<>(contractReq, headers),
                Map.class
        );
        assertThat(contractResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        Boolean isAbove10Pct = (Boolean) contractResp.getBody().get("isAbove10PctScreeningIndicator");
        assertThat(isAbove10Pct).isTrue();

        // 5. Query Diligence Workbench: Ensure blockers are present for Pending Criminal Case and CoC Consent
        ResponseEntity<Map> wbResp = rest.exchange(
                "/api/transactions/" + tx.transactionId() + "/workbench",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                Map.class
        );
        assertThat(wbResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<Map<String, Object>> blockers = (List<Map<String, Object>>) wbResp.getBody().get("blockers");
        assertThat(blockers).isNotEmpty();

        boolean hasCriminalBlocker = blockers.stream().anyMatch(b -> "PENDING_CRIMINAL_LITIGATION".equals(b.get("type")));
        boolean hasCocBlocker = blockers.stream().anyMatch(b -> "CONTRACT_COC_CONSENT_PENDING".equals(b.get("type")));

        assertThat(hasCriminalBlocker).isTrue();
        assertThat(hasCocBlocker).isTrue();
    }
}
