package com.gagroup.ca.materializer;

import com.gagroup.ca.materializer.repository.CaSettledEventRepository;
import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.EnrichedConfirmationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer"
})
@DirtiesContext
@Testcontainers
@EmbeddedKafka(
        partitions = 1,
        topics = {"ca.confirmations.enriched"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
class CaMaterializerIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("caevents")
            .withUsername("causer")
            .withPassword("capass");

    @Autowired EmbeddedKafkaBroker embeddedKafka;
    @Autowired KafkaTemplate<String, EnrichedConfirmationEvent> enrichedTemplate;
    @Autowired CaSettledEventRepository repository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    private EnrichedConfirmationEvent enrichedEvent(String messageId, String isin,
                                                     String eventType, String accountId) {
        var base = new CaConfirmationEvent(
                messageId, "CONF-001", isin, eventType,
                "20261231", new BigDecimal("2500.00"), "CHF", accountId,
                new BigDecimal("1000"), "SETT", "MT566", Instant.now());
        return new EnrichedConfirmationEvent(
                base, "Arthur Dent Holdings", "ARTHURDENTLEI000001", "GA Exchange", "CHF", Instant.now());
    }

    @Test
    void materializeEnrichedEventConsumedFromKafkaRowPersistedInPostgres() {
        // Arrange
        var event = enrichedEvent("IT-MAT-001", "CH0012221716", "DVCA", "ACC-001");

        // Act
        enrichedTemplate.send("ca.confirmations.enriched", event.base().messageId(), event);

        // Assert — row appears in PostgreSQL within 10 seconds
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var row = repository.findById("IT-MAT-001");
            assertThat(row).isPresent();
            assertThat(row.get().getIsin()).isEqualTo("CH0012221716");
            assertThat(row.get().getEventType()).isEqualTo("DVCA");
            assertThat(row.get().getSecurityName()).isEqualTo("Arthur Dent Holdings");
            assertThat(row.get().getMarketOfListing()).isEqualTo("GA Exchange");
            assertThat(row.get().getEnrichedAt()).isNotNull();
            assertThat(row.get().getReceivedAt()).isNotNull();
        });
    }

    @Test
    void materializeFallbackEventRowPersistedWithLookupFailedValues() {
        // Arrange
        var base = new CaConfirmationEvent(
                "IT-MAT-002", "CONF-002", "XX0000000000", "DVCA",
                "20261231", new BigDecimal("100.00"), "CHF", "ACC-002",
                new BigDecimal("50"), "SETT", "seev.036", Instant.now());
        var event = new EnrichedConfirmationEvent(
                base, "LOOKUP_FAILED", "N/A", "N/A", "CHF", Instant.now());

        // Act
        enrichedTemplate.send("ca.confirmations.enriched", event.base().messageId(), event);

        // Assert — fallback row written; pipeline not blocked by bad ref data
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var row = repository.findById("IT-MAT-002");
            assertThat(row).isPresent();
            assertThat(row.get().getSecurityName()).isEqualTo("LOOKUP_FAILED");
            assertThat(row.get().getIssuerLei()).isEqualTo("N/A");
            assertThat(row.get().getIsin()).isEqualTo("XX0000000000");
        });
    }

    @Test
    void materializeDuplicateMessageIdUpsertOverwritesPreviousRow() {
        // Arrange — first write
        var event1 = enrichedEvent("IT-MAT-003", "CH0012221716", "DVCA", "ACC-001");
        enrichedTemplate.send("ca.confirmations.enriched", event1.base().messageId(), event1);
        await().atMost(Duration.ofSeconds(10))
                .until(() -> repository.findById("IT-MAT-003").isPresent());

        // Arrange — duplicate with updated eventType
        var base2 = new CaConfirmationEvent(
                "IT-MAT-003", "CONF-001", "CH0012221716", "BONU",
                "20261231", new BigDecimal("2500.00"), "CHF", "ACC-001",
                new BigDecimal("1000"), "SETT", "MT566", Instant.now());
        var event2 = new EnrichedConfirmationEvent(
                base2, "Arthur Dent Holdings", "ARTHURDENTLEI000001", "GA Exchange", "CHF", Instant.now());

        // Act
        enrichedTemplate.send("ca.confirmations.enriched", event2.base().messageId(), event2);

        // Assert — only one row exists; eventType updated to BONU (upsert by PK)
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(repository.count()).isEqualTo(1);
            assertThat(repository.findById("IT-MAT-003").get().getEventType()).isEqualTo("BONU");
        });
    }

    @Test
    void materializeMultipleDistinctEventsAllRowsPersistedInPostgres() {
        // Arrange
        var e1 = enrichedEvent("IT-MAT-004", "CH0012221716", "DVCA", "ACC-001");
        var e2 = enrichedEvent("IT-MAT-005", "CH0012255580", "BONU", "ACC-002");
        var e3 = enrichedEvent("IT-MAT-006", "CH0038863350", "MRGR", "ACC-003");

        // Act
        enrichedTemplate.send("ca.confirmations.enriched", e1.base().messageId(), e1);
        enrichedTemplate.send("ca.confirmations.enriched", e2.base().messageId(), e2);
        enrichedTemplate.send("ca.confirmations.enriched", e3.base().messageId(), e3);

        // Assert — all three rows in PostgreSQL
        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(repository.count()).isEqualTo(3));
    }
}
