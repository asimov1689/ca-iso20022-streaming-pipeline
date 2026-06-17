package com.gagroup.ca.enricher.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CobolReferenceClientTest {

    @Mock RestTemplate restTemplate;

    CobolReferenceClient client;

    @BeforeEach
    void setUp() {
        client = new CobolReferenceClient(restTemplate);
        ReflectionTestUtils.setField(client, "cobolStubUrl", "http://localhost:8086");
    }

    @Test
    void fetchRefDataCobolStubRespondsReturnsRefData() {
        // Arrange
        var stubResponse = Map.of(
                "securityName",    "Arthur Dent Holdings",
                "issuerLei",       "ARTHURDENTLEI000001",
                "marketOfListing", "GA Exchange",
                "settleCcy",       "CHF");
        when(restTemplate.getForObject("http://localhost:8086/cobol/reference/CH0012221716", Map.class))
                .thenReturn(stubResponse);

        // Act
        Map<String, String> result = client.fetchRefData("CH0012221716");

        // Assert
        assertThat(result).containsEntry("securityName", "Arthur Dent Holdings");
        assertThat(result).containsEntry("marketOfListing", "GA Exchange");
        assertThat(result).containsEntry("settleCcy", "CHF");
    }

    @Test
    void fetchRefDataCobolStubReturnsNullReturnsFallback() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(null);

        // Act
        Map<String, String> result = client.fetchRefData("XX0000000000");

        // Assert
        assertThat(result).containsEntry("securityName", "LOOKUP_FAILED");
        assertThat(result).containsEntry("issuerLei", "N/A");
        assertThat(result).containsEntry("marketOfListing", "N/A");
        assertThat(result).containsEntry("settleCcy", "CHF");
    }

    @Test
    void fetchRefDataCobolStubThrowsExceptionReturnsFallback() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(Map.class)))
                .thenThrow(new RuntimeException("Connection refused"));

        // Act
        Map<String, String> result = client.fetchRefData("CH0012221716");

        // Assert
        assertThat(result).containsEntry("securityName", "LOOKUP_FAILED");
        assertThat(result).containsEntry("issuerLei", "N/A");
    }

    @Test
    void fetchRefDataCallsCorrectEndpointUrl() {
        // Arrange
        when(restTemplate.getForObject(anyString(), eq(Map.class))).thenReturn(Map.of(
                "securityName", "Ford Prefect Ltd", "issuerLei", "FORDPREFECTLEI00001",
                "marketOfListing", "GA Exchange", "settleCcy", "CHF"));

        // Act
        client.fetchRefData("CH0012255580");

        // Assert
        verify(restTemplate).getForObject(
                "http://localhost:8086/cobol/reference/CH0012255580", Map.class);
    }
}
