package com.gagroup.ca.materializer.service;

import com.gagroup.ca.materializer.entity.CaSettledEventEntity;
import com.gagroup.ca.materializer.repository.CaSettledEventRepository;
import com.gagroup.ca.model.EnrichedConfirmationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import java.time.Instant;

@Service
public class CaMaterializerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaMaterializerService.class);

    private final CaSettledEventRepository repository;

    public CaMaterializerService(CaSettledEventRepository repository) {
        this.repository = repository;
    }

    @KafkaListener(topics = "ca.confirmations.enriched", groupId = "ca-materializer-group")
    public void materialize(EnrichedConfirmationEvent event) {
        var entity = CaSettledEventEntity.builder()
                .messageId(event.base().messageId())
                .confirmationRef(event.base().confirmationRef())
                .isin(event.base().isin())
                .eventType(event.base().eventType())
                .settlementDate(event.base().settlementDate())
                .netCashAmount(event.base().netCashAmount())
                .currency(event.base().currency())
                .accountId(event.base().accountId())
                .quantity(event.base().quantity())
                .status(event.base().status())
                .sourceFormat(event.base().sourceFormat())
                .securityName(event.securityName())
                .issuerLei(event.issuerLei())
                .marketOfListing(event.marketOfListing())
                .settleCcy(event.settleCcy())
                .enrichedAt(event.enrichedAt())
                .receivedAt(Instant.now())
                .build();

        repository.save(entity);
        LOGGER.info("Materialized messageId={} isin={} eventType={}",
                entity.getMessageId(), entity.getIsin(), entity.getEventType());
    }
}
