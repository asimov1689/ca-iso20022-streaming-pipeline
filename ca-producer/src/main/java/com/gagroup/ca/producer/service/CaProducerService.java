package com.gagroup.ca.producer.service;

import com.gagroup.ca.model.RawConfirmationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.UUID;

@Service
public class CaProducerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaProducerService.class);
    private static final String TOPIC = "ca.confirmations.raw";

    private final KafkaTemplate<String, RawConfirmationEvent> kafka;

    public CaProducerService(KafkaTemplate<String, RawConfirmationEvent> kafka) {
        this.kafka = kafka;
    }

    public String publish(String payload, String messageType) {
        String id = UUID.randomUUID().toString();
        var event = new RawConfirmationEvent(id, messageType, payload, Instant.now());
        kafka.send(TOPIC, id, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        LOGGER.error("Publish failed {} {}: {}", messageType, id, ex.getMessage());
                    } else {
                        LOGGER.info("Published {} {} partition={}",
                                messageType, id, result.getRecordMetadata().partition());
                    }
                });
        return id;
    }
}
