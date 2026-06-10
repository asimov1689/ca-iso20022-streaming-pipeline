package com.gagroup.ca.formatter.service;

import com.gagroup.ca.formatter.parser.Mt566ConfirmationParser;
import com.gagroup.ca.formatter.parser.MxConfirmationParser;
import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.RawConfirmationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class CaFormatterService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CaFormatterService.class);
    private static final String OUT = "ca.confirmations.formatted";
    private static final String DLQ = "ca.dead-letter";

    private final KafkaTemplate<String, CaConfirmationEvent> formattedKafka;
    private final KafkaTemplate<String, RawConfirmationEvent> rawKafka;
    private final Mt566ConfirmationParser mt566Parser;
    private final MxConfirmationParser mxParser;

    public CaFormatterService(KafkaTemplate<String, CaConfirmationEvent> formattedKafka,
                              KafkaTemplate<String, RawConfirmationEvent> rawKafka,
                              Mt566ConfirmationParser mt566Parser,
                              MxConfirmationParser mxParser) {
        this.formattedKafka = formattedKafka;
        this.rawKafka       = rawKafka;
        this.mt566Parser    = mt566Parser;
        this.mxParser       = mxParser;
    }

    @KafkaListener(topics = "ca.confirmations.raw", groupId = "ca-formatter-group")
    public void consume(RawConfirmationEvent raw) {
        try {
            CaConfirmationEvent confirmation = switch (raw.messageType()) {
                case "MT566"    -> mt566Parser.parse(raw);
                case "seev.036" -> mxParser.parse(raw);
                default -> throw new IllegalArgumentException(
                        "Unknown messageType: " + raw.messageType());
            };
            formattedKafka.send(OUT, confirmation.messageId(), confirmation);
            LOGGER.info("Formatted {} confirmationRef={} isin={}",
                    confirmation.sourceFormat(),
                    confirmation.confirmationRef(),
                    confirmation.isin());
        } catch (Exception e) {
            LOGGER.error("Parse error messageId={}: {}", raw.messageId(), e.getMessage());
            rawKafka.send(DLQ, raw.messageId(), raw);
        }
    }
}
