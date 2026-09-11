package com.syndicate.ingestion;

import com.syndicate.candidatefact.CandidateFact;
import com.syndicate.candidatefact.CandidateFactRepository;
import com.syndicate.candidatefact.CandidateFactSource;
import com.syndicate.common.ResourceNotFoundException;
import com.syndicate.evidence.Evidence;
import com.syndicate.evidence.EvidenceRepository;
import com.syndicate.evidence.FileStorageService;
import com.syndicate.evidence.ProcessingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public EvidenceExtractionService(EvidenceRepository evidenceRepository,
                                      CandidateFactRepository candidateFactRepository,
                                      FileStorageService fileStorageService,
                                      PdfLayoutParser pdfLayoutParser,
                                      TesseractOcrRunner tesseractOcrRunner,
                                      CandidateFactMatcher candidateFactMatcher,
                                      PageImageCache pageImageCache) {
        this.evidenceRepository = evidenceRepository;
        this.candidateFactRepository = candidateFactRepository;
        this.fileStorageService = fileStorageService;
        this.pdfLayoutParser = pdfLayoutParser;
        this.tesseractOcrRunner = tesseractOcrRunner;
        this.candidateFactMatcher = candidateFactMatcher;
        this.pageImageCache = pageImageCache;
    }

    @Transactional(noRollbackFor = Exception.class)
    public void process(UUID evidenceId) {
        Evidence evidence = evidenceRepository.findById(evidenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence not found: " + evidenceId));

        // idempotency: a redelivered message may be reprocessing after a partial
        // previous attempt, so always start from a clean slate for this evidence
        candidateFactRepository.deleteByEvidenceId(evidenceId);
        evidence.setProcessingStatus(ProcessingStatus.PROCESSING);
        evidence.setProcessingError(null);

        try {
            runPipeline(evidence);
        } catch (Exception e) {
            log.warn("Evidence extraction failed for {}: {}", evidenceId, e.getMessage(), e);
            evidence.setProcessingStatus(ProcessingStatus.FAILED);
            evidence.setProcessingError(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
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
            persistCandidates(evidence, candidateFactMatcher.match(allTokens), pageImages);

        } else if (contentType != null && contentType.startsWith("image/")) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                throw new IOException("Could not decode image content");
            }
            List<DocumentToken> tokens = tesseractOcrRunner.run(content, 1, image.getWidth(), image.getHeight());
            ByteArrayOutputStream pngOut = new ByteArrayOutputStream();
            ImageIO.write(image, "png", pngOut);
            persistCandidates(evidence, candidateFactMatcher.match(tokens), Map.of(1, pngOut.toByteArray()));

        } else {
            evidence.setProcessingStatus(ProcessingStatus.NOT_APPLICABLE);
        }
    }

    private void persistCandidates(Evidence evidence, List<MatchedCandidate> matches, Map<Integer, byte[]> pageImages) {
        Set<Integer> cachedPages = new HashSet<>();
        for (MatchedCandidate m : matches) {
            if (cachedPages.add(m.pageNumber())) {
                pageImageCache.save(evidence.getId(), m.pageNumber(), pageImages.get(m.pageNumber()));
            }
            candidateFactRepository.save(toCandidateFact(evidence, m));
        }
        evidence.setProcessingStatus(ProcessingStatus.COMPLETE);
    }

    private CandidateFact toCandidateFact(Evidence evidence, MatchedCandidate m) {
        return new CandidateFact(
                evidence.getWorkstream(), evidence, m.label(), m.value(), m.period(),
                m.pageNumber(), m.bboxX(), m.bboxY(), m.bboxWidth(), m.bboxHeight(),
                m.pageImageWidth(), m.pageImageHeight(), m.source());
    }
}
