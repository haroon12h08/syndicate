package com.syndicate.ingestion;

import com.syndicate.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class EvidenceExtractionListener {

    private static final Logger log = LoggerFactory.getLogger(EvidenceExtractionListener.class);

    private final EvidenceExtractionService evidenceExtractionService;

    public EvidenceExtractionListener(EvidenceExtractionService evidenceExtractionService) {
        this.evidenceExtractionService = evidenceExtractionService;
    }

    @RabbitListener(queues = RabbitMqConfig.QUEUE)
    public void onExtractionJob(Map<String, String> payload) {
        UUID evidenceId = UUID.fromString(payload.get("evidenceId"));
        log.info("Processing evidence extraction job for {}", evidenceId);
        evidenceExtractionService.process(evidenceId);
    }
}
