package com.gagroup.ca.enricher.service;

import com.gagroup.ca.enricher.client.CobolReferenceClient;
import com.gagroup.ca.enricher.entity.EnrichmentLogEntity;
import com.gagroup.ca.enricher.repository.EnrichmentLogRepository;
import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.EnrichedConfirmationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;

@Service
public class CaEnricherService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaEnricherService.class);
    private static final String OUT = "ca.confirmations.enriched";

    private final KafkaTemplate<String, EnrichedConfirmationEvent> kafka;
    private final CobolReferenceClient cobolClient;
    private final EnrichmentLogRepository logRepo;

    public CaEnricherService(KafkaTemplate<String, EnrichedConfirmationEvent> kafka,
                             CobolReferenceClient cobolClient,
                             EnrichmentLogRepository logRepo) {
        this.kafka       = kafka;
        this.cobolClient = cobolClient;
        this.logRepo     = logRepo;
    }

    @KafkaListener(topics = "ca.confirmations.formatted", groupId = "ca-enricher-group")
    public void enrich(CaConfirmationEvent confirmation) {
        long start = System.currentTimeMillis();
        Map<String, String> ref = cobolClient.fetchRefData(confirmation.isin());
        long elapsed = System.currentTimeMillis() - start;

        boolean wasCached = elapsed < 5;

        logRepo.save(EnrichmentLogEntity.builder()
                .messageId(confirmation.messageId())
                .isin(confirmation.isin())
                .securityName(ref.get("securityName"))
                .issuerLei(ref.get("issuerLei"))
                .marketOfListing(ref.get("marketOfListing"))
                .settleCcy(ref.get("settleCcy"))
                .cached(wasCached)
                .cobolResponseMs(wasCached ? null : (int) elapsed)
                .enrichedAt(Instant.now())
                .build());

        var enriched = new EnrichedConfirmationEvent(
                confirmation,
                ref.get("securityName"),
                ref.get("issuerLei"),
                ref.get("marketOfListing"),
                ref.get("settleCcy"),
                Instant.now()
        );

        kafka.send(OUT, enriched.base().messageId(), enriched);
        LOGGER.info("Enriched {} isin={} cached={} ms={}",
                enriched.base().messageId(), enriched.base().isin(), wasCached, elapsed);
    }
}
