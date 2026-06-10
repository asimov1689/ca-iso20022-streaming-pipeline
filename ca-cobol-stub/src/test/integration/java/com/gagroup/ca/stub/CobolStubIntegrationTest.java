package com.gagroup.ca.stub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CobolStubIntegrationTest {

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;

    @Test
    void lookupNestleIsinReturnsOkWithCorrectSecurityAndMarket() {
        // Arrange
        String isin = "CH0012221716";

        // Act
        ResponseEntity<Map> response = rest.getForEntity(
                "http://localhost:" + port + "/cobol/reference/" + isin, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("securityName", "Nestle SA");
        assertThat(response.getBody()).containsEntry("marketOfListing", "GA Exchange");
        assertThat(response.getBody()).containsEntry("issuerLei", "PBLD0EJDB5FWOLXP3B76");
        assertThat(response.getBody()).containsEntry("settleCcy", "CHF");
    }

    @Test
    void lookupWrthlyIsinReturnsOkWithCorrectSecurityAndMarket() {
        // Arrange
        String isin = "CH0012032048";

        // Act
        ResponseEntity<Map> response = rest.getForEntity(
                "http://localhost:" + port + "/cobol/reference/" + isin, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("securityName", "WRTHLY Group AG");
        assertThat(response.getBody()).containsEntry("marketOfListing", "GA Exchange");
        assertThat(response.getBody()).containsEntry("issuerLei", "BFM8T61CT2L1QCEMIK50");
    }

    @Test
    void lookupUnknownIsinReturnsOkWithUnknownDefaults() {
        // Arrange
        String unknownIsin = "XX0000000000";

        // Act
        ResponseEntity<Map> response = rest.getForEntity(
                "http://localhost:" + port + "/cobol/reference/" + unknownIsin, Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("securityName", "UNKNOWN");
        assertThat(response.getBody()).containsEntry("issuerLei", "N/A");
        assertThat(response.getBody()).containsEntry("marketOfListing", "UNKNOWN");
        assertThat(response.getBody()).containsEntry("settleCcy", "CHF");
    }

    @Test
    void lookupAllFourKnownIsinsAllReturnGaExchange() {
        // Arrange
        String[] knownIsins = {"CH0012221716", "CH0012255580", "CH0038863350", "CH0012032048"};

        // Act & Assert
        for (String isin : knownIsins) {
            ResponseEntity<Map> response = rest.getForEntity(
                    "http://localhost:" + port + "/cobol/reference/" + isin, Map.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody())
                    .as("marketOfListing for ISIN %s", isin)
                    .containsEntry("marketOfListing", "GA Exchange");
        }
    }
}
