package com.gagroup.ca.enricher.service;

import com.gagroup.ca.enricher.client.CobolReferenceClient;
import com.gagroup.ca.enricher.entity.EnrichmentLogEntity;
import com.gagroup.ca.enricher.repository.EnrichmentLogRepository;
import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.EnrichedConfirmationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaEnricherServiceTest {

    @Mock KafkaTemplate<String, EnrichedConfirmationEvent> kafka;
    @Mock CobolReferenceClient cobolClient;
    @Mock EnrichmentLogRepository logRepo;

    CaEnricherService service;

    private static final Map<String, String> NESTLE_REF = Map.of(
            "securityName",    "Nestle SA",
            "issuerLei",       "PBLD0EJDB5FWOLXP3B76",
            "marketOfListing", "GA Exchange",
            "settleCcy",       "CHF");

    @BeforeEach
    void setUp() {
        service = new CaEnricherService(kafka, cobolClient, logRepo);
    }

    private CaConfirmationEvent confirmation(String messageId, String isin) {
        return new CaConfirmationEvent(messageId, "CONF-001", isin, "DVCA",
                "20261231", new BigDecimal("2500.00"), "CHF", "ACC-001",
                new BigDecimal("1000"), "SETT", "MT566", Instant.now());
    }

    @Test
    void enrichKnownIsinShouldPublishEnrichedEventWithRefData() {
        // Arrange
        var conf = confirmation("MSG-001", "CH0012221716");
        when(cobolClient.fetchRefData("CH0012221716")).thenReturn(NESTLE_REF);
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.enrich(conf);

        // Assert
        var captor = ArgumentCaptor.forClass(EnrichedConfirmationEvent.class);
        verify(kafka).send(eq("ca.confirmations.enriched"), eq("MSG-001"), captor.capture());
        assertThat(captor.getValue().securityName()).isEqualTo("Nestle SA");
        assertThat(captor.getValue().issuerLei()).isEqualTo("PBLD0EJDB5FWOLXP3B76");
        assertThat(captor.getValue().marketOfListing()).isEqualTo("GA Exchange");
        assertThat(captor.getValue().settleCcy()).isEqualTo("CHF");
        assertThat(captor.getValue().base().isin()).isEqualTo("CH0012221716");
    }

    @Test
    void enrichKnownIsinShouldSaveAuditLogWithCorrectFields() {
        // Arrange
        var conf = confirmation("MSG-002", "CH0012221716");
        when(cobolClient.fetchRefData("CH0012221716")).thenReturn(NESTLE_REF);
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.enrich(conf);

        // Assert
        var captor = ArgumentCaptor.forClass(EnrichmentLogEntity.class);
        verify(logRepo).save(captor.capture());
        assertThat(captor.getValue().getMessageId()).isEqualTo("MSG-002");
        assertThat(captor.getValue().getIsin()).isEqualTo("CH0012221716");
        assertThat(captor.getValue().getSecurityName()).isEqualTo("Nestle SA");
        assertThat(captor.getValue().getMarketOfListing()).isEqualTo("GA Exchange");
        assertThat(captor.getValue().getEnrichedAt()).isNotNull();
    }

    @Test
    void enrichCobolClientReturnsFallbackShouldPublishFallbackEnrichedEvent() {
        // Arrange
        var conf = confirmation("MSG-003", "XX0000000000");
        when(cobolClient.fetchRefData("XX0000000000")).thenReturn(
                Map.of("securityName","LOOKUP_FAILED","issuerLei","N/A",
                       "marketOfListing","N/A","settleCcy","CHF"));
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.enrich(conf);

        // Assert
        var captor = ArgumentCaptor.forClass(EnrichedConfirmationEvent.class);
        verify(kafka).send(eq("ca.confirmations.enriched"), eq("MSG-003"), captor.capture());
        assertThat(captor.getValue().securityName()).isEqualTo("LOOKUP_FAILED");
        assertThat(captor.getValue().issuerLei()).isEqualTo("N/A");
        verify(logRepo).save(any(EnrichmentLogEntity.class));
    }

    @Test
    void enrichShouldAlwaysPublishToEnrichedTopicEvenOnFallback() {
        // Arrange
        var conf = confirmation("MSG-004", "CH0012221716");
        when(cobolClient.fetchRefData(any())).thenReturn(NESTLE_REF);
        when(kafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
        when(logRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        service.enrich(conf);

        // Assert
        verify(kafka, times(1)).send(eq("ca.confirmations.enriched"), anyString(), any());
    }
}
