package com.syndicate.evidence;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.evidence.dto.EvidenceManifestDto;
import com.syndicate.fact.Fact;
import com.syndicate.fact.FactRepository;
import com.syndicate.fact.FactStatus;
import com.syndicate.support.IntegrationTestBase;
import com.syndicate.support.TestApi;
import com.syndicate.support.TransactionFixture;
import com.syndicate.user.User;
import com.syndicate.user.UserRepository;
import com.syndicate.workstream.Workstream;
import com.syndicate.workstream.WorkstreamRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SpatialGroundingIT extends IntegrationTestBase {

    @Autowired TestRestTemplate rest;
    @Autowired CandidateFactRepository candidateFactRepository;
    @Autowired FactRepository factRepository;
    @Autowired EvidenceRepository evidenceRepository;
    @Autowired WorkstreamRepository workstreamRepository;
    @Autowired UserRepository userRepository;

    TestApi api;
    TransactionFixture tx;

    @BeforeEach
    void setUp() {
        api = new TestApi(rest);
        tx = TransactionFixture.create(api, "FINANCIAL_DUE_DILIGENCE");
    }

    private byte[] createMultiPagePdf(int pages) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                    PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                    stream.beginText();
                    stream.setFont(font, 14);
                    stream.newLineAtOffset(50, 700);
                    stream.showText("Page " + i + " content for diligence verification");
                    stream.endText();
                }
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void onDemandPageRenderingAndManifestEndpoint() throws Exception {
        byte[] pdfBytes = createMultiPagePdf(3);
        UUID evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), "audit_report_fy25.pdf",
                pdfBytes, "AUDITED_FINANCIAL_STATEMENT");

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tx.lead().token());

        // 1. Fetch manifest
        ResponseEntity<EvidenceManifestDto> manifestResp = rest.exchange(
                "/api/evidence/" + evidenceId + "/manifest",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                EvidenceManifestDto.class
        );

        assertThat(manifestResp.getStatusCode()).isEqualTo(HttpStatus.OK);
        EvidenceManifestDto manifest = manifestResp.getBody();
        assertThat(manifest).isNotNull();
        assertThat(manifest.evidenceId()).isEqualTo(evidenceId);
        assertThat(manifest.fileName()).isEqualTo("audit_report_fy25.pdf");
        assertThat(manifest.pageCount()).isEqualTo(3);
        assertThat(manifest.contentType()).isEqualTo("application/pdf");
        assertThat(manifest.quality()).isEqualTo(EvidenceQuality.UNREVIEWED);
        assertThat(manifest.retentionState()).isEqualTo(RetentionState.ACTIVE);

        // 2. Fetch page 1 (renders or loads from cache)
        ResponseEntity<byte[]> page1Resp = rest.exchange(
                "/api/evidence/" + evidenceId + "/pages/1/image",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        assertThat(page1Resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page1Resp.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(page1Resp.getBody()).isNotEmpty();

        // 3. Fetch page 3 (rendered on-demand!)
        ResponseEntity<byte[]> page3Resp = rest.exchange(
                "/api/evidence/" + evidenceId + "/pages/3/image",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                byte[].class
        );
        assertThat(page3Resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page3Resp.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(page3Resp.getBody()).isNotEmpty();

        // 4. Request invalid page (out of bounds)
        ResponseEntity<String> invalidPageResp = rest.exchange(
                "/api/evidence/" + evidenceId + "/pages/10/image",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
        );
        assertThat(invalidPageResp.getStatusCode().is4xxClientError()).isTrue();
    }

    @Test
    void manifestTracksSpatialAnchorsAndLinkedFacts() throws Exception {
        byte[] pdfBytes = createMultiPagePdf(2);
        UUID evidenceId = api.uploadEvidence(tx.lead(), tx.workstreamId(), "financials.pdf",
                pdfBytes, "AUDITED_FINANCIAL_STATEMENT");

        Evidence evidence = evidenceRepository.findById(evidenceId).orElseThrow();
        Workstream workstream = workstreamRepository.findById(tx.workstreamId()).orElseThrow();
        User leadUser = userRepository.findById(tx.lead().userId()).orElseThrow();

        // Create a candidate fact on page 2
        CandidateFact candidate = new CandidateFact(
                workstream, evidence, "Total Assets", "15000000", "FY2025",
                2, 120.0, 340.0, 180.0, 32.0, 1700, 2200, CandidateFactSource.TEXT_LAYER
        );
        candidateFactRepository.save(candidate);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tx.lead().token());

        // Manifest should reflect candidate anchor in PENDING state
        ResponseEntity<EvidenceManifestDto> manifest1 = rest.exchange(
                "/api/evidence/" + evidenceId + "/manifest",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                EvidenceManifestDto.class
        );
        assertThat(manifest1.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(manifest1.getBody().anchors()).hasSize(1);
        EvidenceManifestDto.SpatialAnchorDto anchor1 = manifest1.getBody().anchors().get(0);
        assertThat(anchor1.candidateFactId()).isEqualTo(candidate.getId());
        assertThat(anchor1.pageNumber()).isEqualTo(2);
        assertThat(anchor1.bboxX()).isEqualTo(120.0);
        assertThat(anchor1.bboxY()).isEqualTo(340.0);
        assertThat(anchor1.isVerified()).isFalse();

        // Now simulate accepting and verifying into a Fact
        Fact fact = new Fact(workstream, "issuer.assets.total", "Total Assets", "15000000", "INR", "FY2025",
                null, 1, leadUser, Instant.now(), null);
        fact.getEvidence().add(evidence);
        fact.markVerified(leadUser, Instant.now());
        factRepository.save(fact);

        candidate.accept(leadUser, "Verified against balance sheet", fact);
        candidateFactRepository.save(candidate);

        // Fetch manifest again
        ResponseEntity<EvidenceManifestDto> manifest2 = rest.exchange(
                "/api/evidence/" + evidenceId + "/manifest",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                EvidenceManifestDto.class
        );
        assertThat(manifest2.getStatusCode()).isEqualTo(HttpStatus.OK);
        EvidenceManifestDto body2 = manifest2.getBody();
        assertThat(body2.anchors()).hasSize(1);
        EvidenceManifestDto.SpatialAnchorDto verifiedAnchor = body2.anchors().get(0);
        assertThat(verifiedAnchor.isVerified()).isTrue();
        assertThat(verifiedAnchor.resultingFactId()).isEqualTo(fact.getId());
        assertThat(verifiedAnchor.factKey()).isEqualTo("issuer.assets.total");

        assertThat(body2.linkedFacts()).hasSize(1);
        EvidenceManifestDto.LinkedFactDto linkedFact = body2.linkedFacts().get(0);
        assertThat(linkedFact.id()).isEqualTo(fact.getId());
        assertThat(linkedFact.hasSpatialAnchor()).isTrue();
        assertThat(linkedFact.pageNumber()).isEqualTo(2);
        assertThat(linkedFact.status()).isEqualTo(FactStatus.VERIFIED);
    }
}
