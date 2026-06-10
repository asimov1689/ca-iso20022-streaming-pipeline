package com.gagroup.ca.formatter.service;

import com.gagroup.ca.formatter.parser.Mt566ConfirmationParser;
import com.gagroup.ca.formatter.parser.MxConfirmationParser;
import com.gagroup.ca.model.CaConfirmationEvent;
import com.gagroup.ca.model.RawConfirmationEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaFormatterServiceTest {

    @Mock KafkaTemplate<String, CaConfirmationEvent> formattedKafka;
    @Mock KafkaTemplate<String, RawConfirmationEvent> rawKafka;
    @Mock Mt566ConfirmationParser mt566Parser;
    @Mock MxConfirmationParser mxParser;

    CaFormatterService service;

    @BeforeEach
    void setUp() {
        service = new CaFormatterService(formattedKafka, rawKafka, mt566Parser, mxParser);
    }

    private CaConfirmationEvent stubEvent(String messageId, String format) {
        return new CaConfirmationEvent(messageId, "CONF-001", "CH0012221716", "DVCA",
                "20261231", new BigDecimal("2500.00"), "CHF", "ACC-001",
                new BigDecimal("1000"), "SETT", format, Instant.now());
    }

    @Test
    void consumeMt566ShouldParseAndPublishToFormattedTopic() {
        var raw = new RawConfirmationEvent("MSG-001", "MT566", "CONF-001|...", Instant.now());
        var event = stubEvent("MSG-001", "MT566");
        when(mt566Parser.parse(raw)).thenReturn(event);
        when(formattedKafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        service.consume(raw);

        var captor = ArgumentCaptor.forClass(CaConfirmationEvent.class);
        verify(formattedKafka).send(eq("ca.confirmations.formatted"), eq("MSG-001"), captor.capture());
        assertThat(captor.getValue().isin()).isEqualTo("CH0012221716");
        verifyNoInteractions(rawKafka);
    }

    @Test
    void consumeSeev036ShouldDelegateToMxParserAndPublish() {
        var raw = new RawConfirmationEvent("MSG-002", "seev.036", "<Document/>", Instant.now());
        var event = stubEvent("MSG-002", "seev.036");
        when(mxParser.parse(raw)).thenReturn(event);
        when(formattedKafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        service.consume(raw);

        verify(mxParser).parse(raw);
        verify(mt566Parser, never()).parse(any());
        verify(formattedKafka).send(eq("ca.confirmations.formatted"), eq("MSG-002"), any());
        verifyNoInteractions(rawKafka);
    }

    @Test
    void consumeUnknownMessageTypeShouldSendOriginalRawEventToDlq() {
        var raw = new RawConfirmationEvent("MSG-003", "UNKNOWN", "payload", Instant.now());
        when(rawKafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.consume(raw);

        verify(rawKafka).send(eq("ca.dead-letter"), eq("MSG-003"), eq(raw));
        verifyNoInteractions(formattedKafka);
    }

    @Test
    void consumeParseThrowsExceptionShouldSendOriginalRawEventToDlq() {
        var raw = new RawConfirmationEvent("MSG-004", "MT566", "bad|payload", Instant.now());
        when(mt566Parser.parse(raw)).thenThrow(new IllegalArgumentException("parse error"));
        when(rawKafka.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));

        service.consume(raw);

        verify(rawKafka).send(eq("ca.dead-letter"), eq("MSG-004"), eq(raw));
        verifyNoInteractions(formattedKafka);
    }
}
