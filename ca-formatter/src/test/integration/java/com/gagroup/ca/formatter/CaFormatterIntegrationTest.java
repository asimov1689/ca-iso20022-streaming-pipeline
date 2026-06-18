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
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext
@EmbeddedKafka(
        partitions = 1,
        topics = {"ca.confirmations.raw", "ca.confirmations.formatted", "ca.dead-letter"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CaFormatterIntegrationTest {

    private static final String VALID_SEEV036 = """
            <Document>
              <CorpActnConf>
                <ConfRef>CONF-XML-001</ConfRef>
                <FinInstrm><ISIN>CH0012255580</ISIN></FinInstrm>
                <EvtTp>BONU</EvtTp>
                <SttlmDt>20261215</SttlmDt>
                <NetCshAmt><Amt Ccy="CHF">1200.00</Amt></NetCshAmt>
                <AcctId>ACC-XML-001</AcctId>
                <Qty>250</Qty>
                <Sts>SETT</Sts>
              </CorpActnConf>
            </Document>
            """;

    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired KafkaTemplate<String, RawConfirmationEvent> rawTemplate;

    Consumer<String, CaConfirmationEvent> consumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> props = KafkaTestUtils.consumerProps(
                "it-formatter-" + UUID.randomUUID(), "true", embeddedKafka);
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

        ConsumerRecord<String, CaConfirmationEvent> record = pollFormattedByMessageId("IT-001");
        assertThat(record.value().isin()).isEqualTo("CH0012221716");
        assertThat(record.value().sourceFormat()).isEqualTo("MT566");
        assertThat(record.value().confirmationRef()).isEqualTo("CONF-001");
    }

    @Test
    void seev036RawEventShouldBeFormattedAndPublishedToFormattedTopic() {
        var raw = new RawConfirmationEvent("IT-XML-001", "seev.036", VALID_SEEV036, Instant.now());

        rawTemplate.send("ca.confirmations.raw", raw.messageId(), raw);

        ConsumerRecord<String, CaConfirmationEvent> record = pollFormattedByMessageId("IT-XML-001");
        assertThat(record.value().confirmationRef()).isEqualTo("CONF-XML-001");
        assertThat(record.value().isin()).isEqualTo("CH0012255580");
        assertThat(record.value().eventType()).isEqualTo("BONU");
        assertThat(record.value().sourceFormat()).isEqualTo("seev.036");
    }

    @Test
    void unknownMessageTypeShouldRouteOriginalRawEventToDeadLetterQueue() {
        var dlqProps = KafkaTestUtils.consumerProps("it-dlq-" + UUID.randomUUID(), "true", embeddedKafka);
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
                pollRawByMessageId(dlqConsumer, "IT-002");
        assertThat(dlqRecord.key()).isEqualTo("IT-002");
        assertThat(dlqRecord.value().messageId()).isEqualTo(raw.messageId());
        assertThat(dlqRecord.value().messageType()).isEqualTo(raw.messageType());
        assertThat(dlqRecord.value().rawPayload()).isEqualTo(raw.rawPayload());
        dlqConsumer.close();
    }

    @Test
    void malformedSeev036ShouldRouteOriginalRawEventToDeadLetterQueue() {
        var dlqProps = KafkaTestUtils.consumerProps("it-dlq-xml-" + UUID.randomUUID(), "true", embeddedKafka);
        dlqProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gagroup.ca.model");
        Consumer<String, RawConfirmationEvent> dlqConsumer =
                new DefaultKafkaConsumerFactory<String, RawConfirmationEvent>(
                        dlqProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(RawConfirmationEvent.class)
                ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(dlqConsumer, "ca.dead-letter");

        var raw = new RawConfirmationEvent("IT-XML-ERR-001", "seev.036", "<Document>", Instant.now());
        rawTemplate.send("ca.confirmations.raw", raw.messageId(), raw);

        ConsumerRecord<String, RawConfirmationEvent> dlqRecord =
                pollRawByMessageId(dlqConsumer, "IT-XML-ERR-001");
        assertThat(dlqRecord.key()).isEqualTo("IT-XML-ERR-001");
        assertThat(dlqRecord.value().messageType()).isEqualTo("seev.036");
        assertThat(dlqRecord.value().rawPayload()).isEqualTo("<Document>");
        dlqConsumer.close();
    }

    @Test
    void seev036MissingRequiredFieldShouldRouteOriginalRawEventToDeadLetterQueue() {
        var dlqProps = KafkaTestUtils.consumerProps("it-dlq-xml-missing-" + UUID.randomUUID(), "true", embeddedKafka);
        dlqProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gagroup.ca.model");
        Consumer<String, RawConfirmationEvent> dlqConsumer =
                new DefaultKafkaConsumerFactory<String, RawConfirmationEvent>(
                        dlqProps,
                        new StringDeserializer(),
                        new JsonDeserializer<>(RawConfirmationEvent.class)
                ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(dlqConsumer, "ca.dead-letter");

        String missingIsin = """
                <Document>
                  <CorpActnConf>
                    <ConfRef>CONF-XML-002</ConfRef>
                    <FinInstrm></FinInstrm>
                    <EvtTp>BONU</EvtTp>
                    <SttlmDt>20261215</SttlmDt>
                    <NetCshAmt><Amt Ccy="CHF">1200.00</Amt></NetCshAmt>
                    <AcctId>ACC-XML-003</AcctId>
                    <Qty>250</Qty>
                    <Sts>SETT</Sts>
                  </CorpActnConf>
                </Document>
                """;
        var raw = new RawConfirmationEvent("IT-XML-MISSING-001", "seev.036", missingIsin, Instant.now());
        rawTemplate.send("ca.confirmations.raw", raw.messageId(), raw);

        ConsumerRecord<String, RawConfirmationEvent> dlqRecord =
                pollRawByMessageId(dlqConsumer, "IT-XML-MISSING-001");
        assertThat(dlqRecord.key()).isEqualTo("IT-XML-MISSING-001");
        assertThat(dlqRecord.value().messageType()).isEqualTo("seev.036");
        assertThat(dlqRecord.value().rawPayload()).isEqualTo(missingIsin);
        dlqConsumer.close();
    }

    private ConsumerRecord<String, CaConfirmationEvent> pollFormattedByMessageId(String messageId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, CaConfirmationEvent> record : records) {
                if (messageId.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No formatted Kafka record found for messageId=" + messageId);
    }

    private ConsumerRecord<String, RawConfirmationEvent> pollRawByMessageId(
            Consumer<String, RawConfirmationEvent> consumer, String messageId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, RawConfirmationEvent> record : records) {
                if (messageId.equals(record.key())) {
                    return record;
                }
            }
        }
        throw new AssertionError("No DLQ Kafka record found for messageId=" + messageId);
    }
}
