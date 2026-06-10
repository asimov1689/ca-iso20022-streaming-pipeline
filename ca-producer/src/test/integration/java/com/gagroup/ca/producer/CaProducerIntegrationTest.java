package com.gagroup.ca.producer;

import com.gagroup.ca.model.RawConfirmationEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class CaProducerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry r) {
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired TestRestTemplate rest;
    private KafkaConsumer<String, RawConfirmationEvent> consumer;

    @BeforeEach
    void setup() {
        var props = new HashMap<String, Object>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,        kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG,                 "it-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,        "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,   StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(JsonDeserializer.TRUSTED_PACKAGES,              "com.gagroup.ca.model");
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("ca.confirmations.raw"));
    }

    @AfterEach
    void teardown() {
        consumer.close();
    }

    private RawConfirmationEvent pollByMessageId(String messageId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var records = consumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, RawConfirmationEvent> record : records) {
                if (messageId.equals(record.key())) {
                    return record.value();
                }
            }
        }
        Assertions.fail("No Kafka record found for messageId=" + messageId);
        return null;
    }

    @Test
    void postMt566ShouldPublishRawEventToKafka() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        String mt566 = "CONF-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT";

        var response = rest.exchange(
                "/api/v1/ingest/mt566", HttpMethod.POST,
                new HttpEntity<>(mt566, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsKey("messageId");
        String messageId = (String) response.getBody().get("messageId");
        RawConfirmationEvent event = pollByMessageId(messageId);
        assertThat(event.messageType()).isEqualTo("MT566");
        assertThat(event.rawPayload()).contains("DVCA");
    }

    @Test
    void postSeev036ShouldPublishRawEventWithCorrectMessageType() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        String xml = "<Document/>";

        var response = rest.exchange(
                "/api/v1/ingest/seev036", HttpMethod.POST,
                new HttpEntity<>(xml, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String messageId = (String) response.getBody().get("messageId");
        assertThat(pollByMessageId(messageId).messageType()).isEqualTo("seev.036");
    }
}
