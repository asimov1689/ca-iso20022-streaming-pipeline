package com.gagroup.ca.formatter;

import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.RawConfirmationEvent;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import java.time.Instant;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"ca.confirmations.raw", "ca.confirmations.formatted", "ca.dead-letter"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CaFormatterIntegrationTest {

    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired KafkaTemplate<String, RawConfirmationEvent> rawTemplate;

    Consumer<String, CaConfirmationEvent> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("it-formatter", "true", embeddedKafka);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gagroup.ca.model");
        consumer = new DefaultKafkaConsumerFactory<String, CaConfirmationEvent>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(CaConfirmationEvent.class)
        ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(consumer, "ca.confirmations.formatted");
    }

    @AfterEach
    void tearDown() {
        consumer.close();
    }

    @Test
    void mt566RawEventShouldBeFormattedAndPublishedToFormattedTopic() {
        var raw = new RawConfirmationEvent(
                "IT-001", "MT566",
                "CONF-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT",
                Instant.now());

        rawTemplate.send("ca.confirmations.raw", raw.messageId(), raw);

        ConsumerRecord<String, CaConfirmationEvent> record =
                KafkaTestUtils.getSingleRecord(consumer, "ca.confirmations.formatted",
                        java.time.Duration.ofSeconds(10));
        assertThat(record.value().isin()).isEqualTo("CH0012221716");
        assertThat(record.value().sourceFormat()).isEqualTo("MT566");
        assertThat(record.value().confirmationRef()).isEqualTo("CONF-001");
    }

    @Test
    void unknownMessageTypeShouldRouteOriginalRawEventToDeadLetterQueue() {
        var dlqProps = KafkaTestUtils.consumerProps("it-dlq", "true", embeddedKafka);
        dlqProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gagroup.ca.model");
        Consumer<String, RawConfirmationEvent> dlqConsumer =
                new DefaultKafkaConsumerFactory<String, RawConfirmationEvent>(
                        dlqProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(RawConfirmationEvent.class)
                ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(dlqConsumer, "ca.dead-letter");

        var raw = new RawConfirmationEvent("IT-002", "UNKNOWN", "payload", Instant.now());
        rawTemplate.send("ca.confirmations.raw", raw.messageId(), raw);

        ConsumerRecord<String, RawConfirmationEvent> dlqRecord =
                KafkaTestUtils.getSingleRecord(dlqConsumer, "ca.dead-letter",
                        java.time.Duration.ofSeconds(10));
        assertThat(dlqRecord.key()).isEqualTo("IT-002");
        assertThat(dlqRecord.value().messageId()).isEqualTo(raw.messageId());
        assertThat(dlqRecord.value().messageType()).isEqualTo(raw.messageType());
        assertThat(dlqRecord.value().rawPayload()).isEqualTo(raw.rawPayload());
        dlqConsumer.close();
    }
}
