package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.evidence.FileStorageService;
import com.syndicate.evidence.ProcessingStatus;
import com.syndicate.workstream.Workstream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EvidenceExtractionServiceTest {

    @Mock EvidenceRepository evidenceRepository;
    @Mock CandidateFactRepository candidateFactRepository;
    @Mock FileStorageService fileStorageService;
    @Mock PdfLayoutParser pdfLayoutParser;
    @Mock TesseractOcrRunner tesseractOcrRunner;
    @Mock CandidateFactMatcher candidateFactMatcher;
    @Mock PageImageCache pageImageCache;
    @Mock Workstream workstream;

    EvidenceExtractionService service;
    UUID evidenceId;
    Evidence evidence;

    @BeforeEach
    void setUp() {
        service = new EvidenceExtractionService(evidenceRepository, candidateFactRepository, fileStorageService,
                pdfLayoutParser, tesseractOcrRunner, candidateFactMatcher, pageImageCache);
        evidenceId = UUID.randomUUID();
        evidence = mock(Evidence.class);
        // getId()/getWorkstream() are only exercised by the success-path test that reaches
        // persistCandidates(); mark them lenient so the other tests aren't flagged for
        // "unnecessary stubbing" under Mockito's strict-stubs default.
        lenient().when(evidence.getId()).thenReturn(evidenceId);
        lenient().when(evidence.getWorkstream()).thenReturn(workstream);
        when(evidenceRepository.findById(evidenceId)).thenReturn(Optional.of(evidence));
    }

    @Test
    void deletesExistingCandidatesBeforeReprocessing() throws Exception {
        when(evidence.getContentType()).thenReturn("text/plain");

        service.process(evidenceId);

        verify(candidateFactRepository).deleteByEvidenceId(evidenceId);
    }

    @Test
    void marksNotApplicableForUnsupportedContentType() throws Exception {
        when(evidence.getContentType()).thenReturn("application/msword");

        service.process(evidenceId);

        verify(evidence).setProcessingStatus(ProcessingStatus.NOT_APPLICABLE);
        verifyNoInteractions(pdfLayoutParser, tesseractOcrRunner);
    }

    @Test
    void processesPdfTextLayerAndPersistsCandidates() throws Exception {
        when(evidence.getContentType()).thenReturn("application/pdf");
        when(evidence.getStoragePath()).thenReturn("some-file.pdf");
        byte[] pdfBytes = {1, 2, 3};
        when(fileStorageService.readBytes("some-file.pdf")).thenReturn(pdfBytes);

        DocumentToken token = new DocumentToken("Revenue", 1, 10, 10, 5, 5, 1000, 1400, CandidateFactSource.TEXT_LAYER);
        byte[] pagePng = {9, 9};
        PdfLayoutParser.PageResult page = new PdfLayoutParser.PageResult(1, List.of(token), pagePng, 1000, 1400, false);
        when(pdfLayoutParser.parse(pdfBytes)).thenReturn(List.of(page));

        MatchedCandidate matched = new MatchedCandidate("Revenue", "100", "FY2026", 1, 10, 10, 5, 5, 1000, 1400,
                CandidateFactSource.TEXT_LAYER);
        when(candidateFactMatcher.match(List.of(token))).thenReturn(List.of(matched));

        service.process(evidenceId);

        verify(pageImageCache).save(evidenceId, 1, pagePng);
        verify(candidateFactRepository).save(any(CandidateFact.class));
        verify(evidence).setProcessingStatus(ProcessingStatus.COMPLETE);
        verifyNoInteractions(tesseractOcrRunner);
    }

    @Test
    void recordsFailureAndRethrowsWhenParsingBlowsUp() throws Exception {
        when(evidence.getContentType()).thenReturn("application/pdf");
        when(evidence.getStoragePath()).thenReturn("broken.pdf");
        when(fileStorageService.readBytes("broken.pdf")).thenReturn(new byte[]{1});
        when(pdfLayoutParser.parse(any())).thenThrow(new java.io.IOException("corrupt PDF"));

        assertThatThrownBy(() -> service.process(evidenceId))
                .isInstanceOf(EvidenceExtractionException.class);

        verify(evidence).setProcessingStatus(ProcessingStatus.FAILED);
        verify(evidence).setProcessingError(contains("corrupt PDF"));
    }
}
