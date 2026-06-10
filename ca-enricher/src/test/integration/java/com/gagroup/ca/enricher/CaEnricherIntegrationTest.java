package com.gagroup.ca.enricher;

import com.gagroup.ca.enricher.repository.EnrichmentLogRepository;
import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.EnrichedConfirmationEvent;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@DirtiesContext
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"ca.confirmations.formatted", "ca.confirmations.enriched"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CaEnricherIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("caevents")
            .withUsername("causer")
            .withPassword("capass");

    static WireMockServer wireMock;

    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired KafkaTemplate<String, CaConfirmationEvent> formattedTemplate;
    @Autowired EnrichmentLogRepository logRepo;

    Consumer<String, EnrichedConfirmationEvent> enrichedConsumer;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("cobol.stub.url", () -> "http://localhost:" + wireMock.port());
    }

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        Map<String, Object> props = KafkaTestUtils.consumerProps("it-enricher", "true", embeddedKafka);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.gagroup.ca.model");
        enrichedConsumer = new DefaultKafkaConsumerFactory<String, EnrichedConfirmationEvent>(
                props,
                new StringDeserializer(),
                new JsonDeserializer<>(EnrichedConfirmationEvent.class)
        ).createConsumer();
        embeddedKafka.consumeFromAnEmbeddedTopic(enrichedConsumer, "ca.confirmations.enriched");
    }

    @AfterEach
    void tearDown() {
        enrichedConsumer.close();
    }

    @Test
    void enrichFormattedEventShouldPublishEnrichedEventAndPersistAuditLog() {
        // Arrange
        wireMock.stubFor(get(urlEqualTo("/cobol/reference/CH0012221716"))
                .willReturn(okJson("""
                        {"securityName":"Nestle SA","issuerLei":"PBLD0EJDB5FWOLXP3B76",
                         "marketOfListing":"GA Exchange","settleCcy":"CHF"}
                        """)));
        var confirmation = new CaConfirmationEvent(
                "IT-ENRICH-001", "CONF-001", "CH0012221716", "DVCA",
                "20261231", new BigDecimal("2500.00"), "CHF", "ACC-001",
                new BigDecimal("1000"), "SETT", "MT566", Instant.now());

        // Act
        formattedTemplate.send("ca.confirmations.formatted", confirmation.messageId(), confirmation);

        // Assert — enriched event on Kafka
        ConsumerRecord<String, EnrichedConfirmationEvent> record =
                pollByMessageId("IT-ENRICH-001");
        assertThat(record.key()).isEqualTo("IT-ENRICH-001");
        assertThat(record.value().securityName()).isEqualTo("Nestle SA");
        assertThat(record.value().marketOfListing()).isEqualTo("GA Exchange");
        assertThat(record.value().base().isin()).isEqualTo("CH0012221716");

        // Assert — audit row persisted in PostgreSQL
        var logEntry = logRepo.findByMessageId("IT-ENRICH-001");
        assertThat(logEntry).isPresent();
        assertThat(logEntry.get().getSecurityName()).isEqualTo("Nestle SA");
        assertThat(logEntry.get().getIsin()).isEqualTo("CH0012221716");
        assertThat(logEntry.get().getEnrichedAt()).isNotNull();
    }

    @Test
    void enrichCobolStubUnavailableShouldPublishFallbackEnrichedEventAndPersistAuditLog() {
        // Arrange
        wireMock.stubFor(get(urlEqualTo("/cobol/reference/CH0012032048"))
                .willReturn(serverError()));
        var confirmation = new CaConfirmationEvent(
                "IT-ENRICH-002", "CONF-002", "CH0012032048", "DVCA",
                "20261231", new BigDecimal("500.00"), "CHF", "ACC-002",
                new BigDecimal("200"), "SETT", "MT566", Instant.now());

        // Act
        formattedTemplate.send("ca.confirmations.formatted", confirmation.messageId(), confirmation);

        // Assert — fallback enriched event published
        ConsumerRecord<String, EnrichedConfirmationEvent> record =
                pollByMessageId("IT-ENRICH-002");
        assertThat(record.key()).isEqualTo("IT-ENRICH-002");
        assertThat(record.value().securityName()).isEqualTo("LOOKUP_FAILED");
        assertThat(record.value().issuerLei()).isEqualTo("N/A");

        // Assert — audit row still persisted
        assertThat(logRepo.findByMessageId("IT-ENRICH-002")).isPresent();
    }

    private ConsumerRecord<String, EnrichedConfirmationEvent> pollByMessageId(String messageId) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            var records = enrichedConsumer.poll(Duration.ofMillis(250));
            for (ConsumerRecord<String, EnrichedConfirmationEvent> record : records) {
                if (messageId.equals(record.key())) {
                    return record;
                }
            }
        }

        Assertions.fail("No Kafka record found for messageId=" + messageId);
        return null;
    }
}
