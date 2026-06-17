package com.gagroup.ca.materializer.service;

import com.gagroup.ca.materializer.entity.CaSettledEventEntity;
import com.gagroup.ca.materializer.repository.CaSettledEventRepository;
import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.EnrichedConfirmationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaMaterializerServiceTest {

    @Mock CaSettledEventRepository repository;

    CaMaterializerService service;

    @BeforeEach
    void setUp() {
        service = new CaMaterializerService(repository);
    }

    private EnrichedConfirmationEvent enrichedEvent(String messageId, String isin) {
        var base = new CaConfirmationEvent(
                messageId, "CONF-001", isin, "DVCA",
                "20261231", new BigDecimal("2500.00"), "CHF", "ACC-001",
                new BigDecimal("1000"), "SETT", "MT566", Instant.now());
        return new EnrichedConfirmationEvent(
                base, "Arthur Dent Holdings", "ARTHURDENTLEI000001", "GA Exchange", "CHF", Instant.now());
    }

    @Test
    void materializeValidEnrichedEventSavesEntityWithAllBaseFields() {
        // Arrange
        var event = enrichedEvent("MSG-001", "CH0012221716");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.materialize(event);

        // Assert
        var captor = ArgumentCaptor.forClass(CaSettledEventEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getMessageId()).isEqualTo("MSG-001");
        assertThat(saved.getIsin()).isEqualTo("CH0012221716");
        assertThat(saved.getConfirmationRef()).isEqualTo("CONF-001");
        assertThat(saved.getEventType()).isEqualTo("DVCA");
        assertThat(saved.getSettlementDate()).isEqualTo("20261231");
        assertThat(saved.getNetCashAmount()).isEqualByComparingTo("2500.00");
        assertThat(saved.getCurrency()).isEqualTo("CHF");
        assertThat(saved.getAccountId()).isEqualTo("ACC-001");
        assertThat(saved.getStatus()).isEqualTo("SETT");
        assertThat(saved.getSourceFormat()).isEqualTo("MT566");
    }

    @Test
    void materializeValidEnrichedEventSavesEntityWithAllEnrichmentFields() {
        // Arrange
        var event = enrichedEvent("MSG-002", "CH0012221716");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.materialize(event);

        // Assert
        var captor = ArgumentCaptor.forClass(CaSettledEventEntity.class);
        verify(repository).save(captor.capture());
        var saved = captor.getValue();
        assertThat(saved.getSecurityName()).isEqualTo("Arthur Dent Holdings");
        assertThat(saved.getIssuerLei()).isEqualTo("ARTHURDENTLEI000001");
        assertThat(saved.getMarketOfListing()).isEqualTo("GA Exchange");
        assertThat(saved.getSettleCcy()).isEqualTo("CHF");
        assertThat(saved.getEnrichedAt()).isNotNull();
        assertThat(saved.getReceivedAt()).isNotNull();
    }

    @Test
    void materializeDuplicateMessageIdCallsSaveAgainForUpsert() {
        // Arrange
        var event1 = enrichedEvent("MSG-003", "CH0012221716");
        var event2 = enrichedEvent("MSG-003", "CH0012221716");
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.materialize(event1);
        service.materialize(event2);

        // Assert — save called twice; JPA merge handles upsert by PK
        verify(repository, times(2)).save(any(CaSettledEventEntity.class));
    }

    @Test
    void materializeFallbackEnrichedEventSavesLookupFailedSecurityName() {
        // Arrange
        var base = new CaConfirmationEvent(
                "MSG-004", "CONF-002", "XX0000000000", "DVCA",
                "20261231", new BigDecimal("100.00"), "CHF", "ACC-002",
                new BigDecimal("50"), "SETT", "seev.036", Instant.now());
        var event = new EnrichedConfirmationEvent(
                base, "LOOKUP_FAILED", "N/A", "N/A", "CHF", Instant.now());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.materialize(event);

        // Assert — fallback values persisted as-is, pipeline not broken
        var captor = ArgumentCaptor.forClass(CaSettledEventEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getSecurityName()).isEqualTo("LOOKUP_FAILED");
        assertThat(captor.getValue().getIssuerLei()).isEqualTo("N/A");
        assertThat(captor.getValue().getMessageId()).isEqualTo("MSG-004");
    }
}
