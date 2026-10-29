package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.candidatefact.CandidateFactStatus;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.evidence.FileStorageService;
import com.syndicate.evidence.ProcessingStatus;
import com.syndicate.stream.EvidenceStatusEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EvidenceExtractionService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExtractionService.class);

    private final EvidenceRepository evidenceRepository;
    private final CandidateFactRepository candidateFactRepository;
    private final FileStorageService fileStorageService;
    private final PdfLayoutParser pdfLayoutParser;
    private final TesseractOcrRunner tesseractOcrRunner;
    private final CandidateFactMatcher candidateFactMatcher;
    private final PageImageCache pageImageCache;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;
    private final com.syndicate.ai.runs.ExtractionRunService extractionRunService;
    private final com.syndicate.ai.classification.AiDocumentClassifier aiDocumentClassifier;
    private final com.syndicate.ai.extraction.AiCandidateFactExtractor aiCandidateFactExtractor;

    public EvidenceExtractionService(EvidenceRepository evidenceRepository,
                                      CandidateFactRepository candidateFactRepository,
                                      FileStorageService fileStorageService,
                                      PdfLayoutParser pdfLayoutParser,
                                      TesseractOcrRunner tesseractOcrRunner,
                                      CandidateFactMatcher candidateFactMatcher,
                                      PageImageCache pageImageCache,
                                      org.springframework.context.ApplicationEventPublisher eventPublisher,
                                      com.syndicate.ai.runs.ExtractionRunService extractionRunService,
                                      com.syndicate.ai.classification.AiDocumentClassifier aiDocumentClassifier,
                                      com.syndicate.ai.extraction.AiCandidateFactExtractor aiCandidateFactExtractor) {
        this.evidenceRepository = evidenceRepository;
        this.candidateFactRepository = candidateFactRepository;
        this.fileStorageService = fileStorageService;
        this.pdfLayoutParser = pdfLayoutParser;
        this.tesseractOcrRunner = tesseractOcrRunner;
        this.candidateFactMatcher = candidateFactMatcher;
        this.pageImageCache = pageImageCache;
        this.eventPublisher = eventPublisher;
        this.extractionRunService = extractionRunService;
        this.aiDocumentClassifier = aiDocumentClassifier;
        this.aiCandidateFactExtractor = aiCandidateFactExtractor;
    }

    @Transactional(noRollbackFor = Exception.class)
    public void process(UUID evidenceId) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found: " + evidenceId));

        // idempotency: a redelivered message may be reprocessing after a partial previous
        // attempt, so clear this evidence's unreviewed candidates. Reviewed ones are kept: an
        // accepted candidate is the fact's link back to its page region.
        candidateFactRepository.deleteByEvidenceIdAndStatus(evidenceId, CandidateFactStatus.PENDING);
        evidence.setProcessingStatus(ProcessingStatus.PROCESSING);
        evidence.setProcessingError(null);
        publishStatus(evidence, 0);

        try {
            runPipeline(evidence);
        } catch (Exception e) {
            log.warn("Evidence extraction failed for {}: {}", evidenceId, e.getMessage(), e);
            evidence.setProcessingStatus(ProcessingStatus.FAILED);
            evidence.setProcessingError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            publishStatus(evidence, 0);
            throw new EvidenceExtractionException(evidenceId, e);
        }
    }

    private void runPipeline(Evidence evidence) throws IOException {
        byte[] content = fileStorageService.readBytes(evidence.getStoragePath());
        String contentType = evidence.getContentType();

        if ("application/pdf".equals(contentType)) {
            List<PdfLayoutParser.PageResult> pages = pdfLayoutParser.parse(content);
            Map<Integer, byte[]> pageImages = new HashMap<>();
            List<DocumentToken> allTokens = new ArrayList<>();

            for (PdfLayoutParser.PageResult page : pages) {
                pageImages.put(page.pageNumber, page.renderedPng);
                if (page.needsOcr) {
                    allTokens.addAll(tesseractOcrRunner.run(page.renderedPng, page.pageNumber,
                            page.imageWidth, page.imageHeight));
                } else {
                    allTokens.addAll(page.tokens);
                }
            }
            executeExtractionPipeline(evidence, allTokens, pageImages);

        } else if (contentType != null && contentType.startsWith("image/")) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IOException("Could not decode image content");
            }
            List<DocumentToken> tokens = tesseractOcrRunner.run(content, 1, image.getWidth(), image.getHeight());
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(image, "png", pngOut);
            executeExtractionPipeline(evidence, tokens, Map.of(1, pngOut.toByteArray()));

        } else if ("text/plain".equals(contentType)) {
            String text = new String(content, java.nio.charset.StandardCharsets.UTF_8);
            List<DocumentToken> tokens = new ArrayList<>();
            String[] lines = text.split("\r?\\n");
            double y = 20.0;
            for (String line : lines) {
                if (!line.isBlank()) {
                    tokens.add(new DocumentToken(line, 1, 50.0, y, 400.0, 15.0, 1000, 1400, CandidateFactSource.TEXT_LAYER));
                    y += 20.0;
                }
            }
            executeExtractionPipeline(evidence, tokens, Map.of());

        } else {
            evidence.setProcessingStatus(ProcessingStatus.NOT_APPLICABLE);
            publishStatus(evidence, 0);
        }
    }

    /**
     * Announced after the surrounding transaction commits, so a watcher that immediately refetches
     * sees the committed state rather than racing it.
     */
    private void publishStatus(Evidence evidence, int candidateCount) {
        EvidenceStatusEvent event = new EvidenceStatusEvent(
                evidence.getWorkstream().getTransaction().getId(), evidence.getId(),
                evidence.getFileName(), evidence.getProcessingStatus(),
                evidence.getProcessingError(), candidateCount);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    eventPublisher.publishEvent(event);
                }
            });
        } else {
            eventPublisher.publishEvent(event);
        }
    }

    private void executeExtractionPipeline(Evidence evidence, List<DocumentToken> tokens, Map<Integer, byte[]> pageImages) {
        if (pageImages != null) {
            pageImages.forEach((pageNumber, pngBytes) -> {
                if (pngBytes != null && !pageImageCache.exists(evidence.getId(), pageNumber)) {
                    pageImageCache.save(evidence.getId(), pageNumber, pngBytes);
                }
            });
        }

        String fullExtractedText = tokens.stream().map(DocumentToken::text).collect(java.util.stream.Collectors.joining(" "));
        evidence.setExtractedText(fullExtractedText);

        int totalCandidates = 0;

        // 1. Deterministic Pass
        long detStart = System.currentTimeMillis();
        com.syndicate.ai.runs.ExtractionRun detRun = extractionRunService.startRun(evidence, com.syndicate.ai.runs.ExtractionRunType.DETERMINISTIC);
        try {
            List<MatchedCandidate> matches = candidateFactMatcher.match(tokens);
            for (MatchedCandidate m : matches) {
                candidateFactRepository.save(toCandidateFact(evidence, m));
            }
            totalCandidates += matches.size();
            long detDuration = Math.max(1, System.currentTimeMillis() - detStart);
            extractionRunService.completeRun(detRun, matches.size(), null, detDuration);
        } catch (Exception e) {
            long detDuration = Math.max(1, System.currentTimeMillis() - detStart);
            extractionRunService.failRun(detRun, e.getMessage(), detDuration);
            throw e;
        }

        // 2. AI Augmentation Pass (Fault-tolerant, non-blocking)
        long aiStart = System.currentTimeMillis();
        com.syndicate.ai.runs.ExtractionRun aiRun = extractionRunService.startRun(evidence, com.syndicate.ai.runs.ExtractionRunType.AI);
        try {
            // AI Document Classification (if needed)
            if (evidence.getDocumentType() == null || evidence.getDocumentType() == com.syndicate.evidence.EvidenceDocumentType.OTHER) {
                String fullText = tokens.stream().map(DocumentToken::text).collect(java.util.stream.Collectors.joining(" "));
                aiDocumentClassifier.classify(evidence, fullText);
            }

            // AI Candidate Fact Extraction
            int defaultW = !tokens.isEmpty() ? tokens.get(0).pageImageWidth() : 1000;
            int defaultH = !tokens.isEmpty() ? tokens.get(0).pageImageHeight() : 1400;
            com.syndicate.ai.extraction.ExtractedCandidateResult aiResult =
                    aiCandidateFactExtractor.extract(evidence, tokens, defaultW, defaultH);

            for (CandidateFact cf : aiResult.candidateFacts()) {
                candidateFactRepository.save(cf);
            }

            totalCandidates += aiResult.candidateFacts().size();
            long aiDuration = Math.max(1, System.currentTimeMillis() - aiStart);
            extractionRunService.completeRun(aiRun, aiResult.candidateFacts().size(), aiResult.artifact(), aiDuration);
        } catch (Exception e) {
            log.warn("AI extraction augmentation failed for evidence {}: {}", evidence.getId(), e.getMessage());
            long aiDuration = Math.max(1, System.currentTimeMillis() - aiStart);
            extractionRunService.failRun(aiRun, e.getMessage(), aiDuration);
            // Fault-tolerance invariant: deterministic candidates remain saved and evidence processing completes!
        }

        evidence.setProcessingStatus(ProcessingStatus.COMPLETE);
        publishStatus(evidence, totalCandidates);
    }

    private CandidateFact toCandidateFact(Evidence evidence, MatchedCandidate m) {
        return new CandidateFact(
                evidence.getWorkstream(), evidence, m.label(), m.value(), m.period(),
                m.pageNumber(), m.bboxX(), m.bboxY(), m.bboxWidth(), m.bboxHeight(),
                m.pageImageWidth(), m.pageImageHeight(), m.source());
    }
}
