package com.gagroup.ca.producer.service;

import com.gagroup.ca.model.RawConfirmationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaProducerServiceTest {

    @Mock KafkaTemplate<String, RawConfirmationEvent> kafkaTemplate;
    @InjectMocks CaProducerService service;

    @Test
    void publishMt566ShouldReturnUuidAndPublishToCorrectTopic() {
        String payload = "CONF-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT";
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        String messageId = service.publish(payload, "MT566");

        assertThat(messageId).isNotBlank().hasSize(36);
        var captor = ArgumentCaptor.forClass(RawConfirmationEvent.class);
        verify(kafkaTemplate).send(eq("ca.confirmations.raw"), eq(messageId), captor.capture());
        assertThat(captor.getValue().messageType()).isEqualTo("MT566");
        assertThat(captor.getValue().rawPayload()).contains("DVCA");
        assertThat(captor.getValue().receivedAt()).isNotNull();
    }

    @Test
    void publishSeev036ShouldPublishToRawTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        service.publish("<Document/>", "seev.036");

        verify(kafkaTemplate).send(eq("ca.confirmations.raw"), anyString(), any());
    }

    @Test
    void publishShouldGenerateUniqueMessageIds() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        String id1 = service.publish("payload1", "MT566");
        String id2 = service.publish("payload2", "MT566");

        assertThat(id1).isNotEqualTo(id2);
    }
}
