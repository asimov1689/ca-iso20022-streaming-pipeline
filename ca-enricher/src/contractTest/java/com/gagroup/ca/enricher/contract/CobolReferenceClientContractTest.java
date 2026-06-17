package com.gagroup.ca.enricher.contract;

import com.gagroup.ca.enricher.client.CobolReferenceClient;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.notFound;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.serverError;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract tests — verify the HTTP contract between ca-enricher (consumer)
 * and ca-cobol-stub (provider): correct URL shape, expected JSON fields,
 * and graceful fallback on provider errors.
 * No Spring context, no Kafka, no database.
 */
class CobolReferenceClientContractTest {

    @RegisterExtension
    static WireMockExtension wm = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    CobolReferenceClient client;

    @BeforeEach
    void setUp() {
        client = new CobolReferenceClient(new RestTemplate());
        ReflectionTestUtils.setField(client, "cobolStubUrl", wm.baseUrl());
    }

    @Test
    void fetchRefDataKnownIsinContractReturnsAllRequiredFields() {
        // Arrange
        wm.stubFor(get(urlEqualTo("/cobol/reference/CH0012221716"))
                .willReturn(okJson("""
                        {
                          "securityName":    "Arthur Dent Holdings",
                          "issuerLei":       "ARTHURDENTLEI000001",
                          "marketOfListing": "GA Exchange",
                          "settleCcy":       "CHF"
                        }
                        """)));

        // Act
        Map<String, String> result = client.fetchRefData("CH0012221716");

        // Assert — all fields the enricher depends on are present
        assertThat(result).containsKey("securityName");
        assertThat(result).containsKey("issuerLei");
        assertThat(result).containsKey("marketOfListing");
        assertThat(result).containsKey("settleCcy");
        assertThat(result.get("securityName")).isEqualTo("Arthur Dent Holdings");
        assertThat(result.get("marketOfListing")).isEqualTo("GA Exchange");

        // Assert — provider was called with correct URL path
        wm.verify(getRequestedFor(urlEqualTo("/cobol/reference/CH0012221716")));
    }

    @Test
    void fetchRefDataUnknownIsinContractReturnsUnknownDefaults() {
        // Arrange
        wm.stubFor(get(urlPathMatching("/cobol/reference/.*"))
                .willReturn(okJson("""
                        {
                          "securityName":    "UNKNOWN",
                          "issuerLei":       "N/A",
                          "marketOfListing": "UNKNOWN",
                          "settleCcy":       "CHF"
                        }
                        """)));

        // Act
        Map<String, String> result = client.fetchRefData("XX0000000000");

        // Assert — consumer handles unknown-ISIN response correctly
        assertThat(result.get("securityName")).isEqualTo("UNKNOWN");
        assertThat(result.get("issuerLei")).isEqualTo("N/A");
    }

    @Test
    void fetchRefDataProviderReturns500ConsumerFallsBackGracefully() {
        // Arrange
        wm.stubFor(get(urlEqualTo("/cobol/reference/CH0038863350"))
                .willReturn(serverError()));

        // Act
        Map<String, String> result = client.fetchRefData("CH0038863350");

        // Assert — consumer never throws; fallback values returned
        assertThat(result).containsEntry("securityName", "LOOKUP_FAILED");
        assertThat(result).containsEntry("issuerLei", "N/A");
        assertThat(result).containsEntry("marketOfListing", "N/A");
        assertThat(result).containsEntry("settleCcy", "CHF");
    }

    @Test
    void fetchRefDataProviderReturns404ConsumerFallsBackGracefully() {
        // Arrange
        wm.stubFor(get(urlEqualTo("/cobol/reference/CH0012032048"))
                .willReturn(notFound()));

        // Act
        Map<String, String> result = client.fetchRefData("CH0012032048");

        // Assert — consumer treats 404 as fallback, not an exception
        assertThat(result).containsEntry("securityName", "LOOKUP_FAILED");
    }

    @Test
    void fetchRefDataRequestUsesGetMethodAndCorrectPath() {
        // Arrange
        wm.stubFor(get(urlEqualTo("/cobol/reference/CH0012255580"))
                .willReturn(okJson("""
                        {"securityName":"Ford Prefect Ltd","issuerLei":"FORDPREFECTLEI00001",
                         "marketOfListing":"GA Exchange","settleCcy":"CHF"}
                        """)));

        // Act
        client.fetchRefData("CH0012255580");

        // Assert — HTTP method and path match the provider contract
        wm.verify(1, getRequestedFor(urlEqualTo("/cobol/reference/CH0012255580")));
    }
}
