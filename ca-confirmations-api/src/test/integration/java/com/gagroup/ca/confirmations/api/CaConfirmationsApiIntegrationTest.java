package com.gagroup.ca.confirmations.api;

import com.gagroup.ca.confirmations.api.entity.CaSettledEventReadEntity;
import com.gagroup.ca.confirmations.api.repository.CaSettledEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.jpa.hibernate.ddl-auto=create-drop"
)
@Testcontainers
class CaConfirmationsApiIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("caevents")
            .withUsername("causer")
            .withPassword("capass");

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
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

    private CaSettledEventReadEntity entity(String messageId, String isin, String eventType,
                                             String accountId, String settlementDate) {
        var e = new CaSettledEventReadEntity();
        e.setMessageId(messageId);
        e.setIsin(isin);
        e.setEventType(eventType);
        e.setConfirmationRef("CONF-001");
        e.setSettlementDate(settlementDate);
        e.setNetCashAmount(new BigDecimal("2500.00"));
        e.setCurrency("CHF");
        e.setAccountId(accountId);
        e.setQuantity(new BigDecimal("1000"));
        e.setStatus("SETT");
        e.setSourceFormat("MT566");
        e.setSecurityName("Arthur Dent Holdings");
        e.setIssuerLei("ARTHURDENTLEI000001");
        e.setMarketOfListing("GA Exchange");
        e.setSettleCcy("CHF");
        e.setEnrichedAt(Instant.now());
        return e;
    }

    private String url(String path) {
        return "http://localhost:" + port + "/api/v1/settled-confirmations" + path;
    }

    @Test
    void getExistingRecordReturns200WithCorrectBody() {
        // Arrange
        repository.save(entity("MSG-IT-001", "CH0012221716", "DVCA", "ACC-001", "20261231"));

        // Act
        ResponseEntity<CaSettledEventReadEntity> response =
                rest.getForEntity(url("/MSG-IT-001"), CaSettledEventReadEntity.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessageId()).isEqualTo("MSG-IT-001");
        assertThat(response.getBody().getIsin()).isEqualTo("CH0012221716");
        assertThat(response.getBody().getSecurityName()).isEqualTo("Arthur Dent Holdings");
        assertThat(response.getBody().getMarketOfListing()).isEqualTo("GA Exchange");
    }

    @Test
    void getUnknownRecordReturns404() {
        // Arrange — no records saved

        // Act
        ResponseEntity<String> response = rest.getForEntity(url("/DOES-NOT-EXIST"), String.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void listByIsinReturnsAllMatchingEvents() {
        // Arrange
        repository.save(entity("MSG-IT-002", "CH0012221716", "DVCA", "ACC-001", "20261231"));
        repository.save(entity("MSG-IT-003", "CH0012221716", "BONU", "ACC-002", "20261215"));
        repository.save(entity("MSG-IT-004", "CH0038863350", "DVCA", "ACC-001", "20261231"));

        // Act
        ResponseEntity<CaSettledEventReadEntity[]> response = rest.getForEntity(
                url("?isin=CH0012221716"), CaSettledEventReadEntity[].class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
        assertThat(response.getBody()).allMatch(e -> e.getIsin().equals("CH0012221716"));
    }

    @Test
    void listByIsinAndEventTypeReturnsIntersection() {
        // Arrange
        repository.save(entity("MSG-IT-005", "CH0012221716", "DVCA", "ACC-001", "20261231"));
        repository.save(entity("MSG-IT-006", "CH0012221716", "MRGR", "ACC-001", "20261215"));
        repository.save(entity("MSG-IT-007", "CH0038863350", "DVCA", "ACC-001", "20261231"));

        // Act
        ResponseEntity<CaSettledEventReadEntity[]> response = rest.getForEntity(
                url("?isin=CH0012221716&eventType=DVCA"), CaSettledEventReadEntity[].class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getMessageId()).isEqualTo("MSG-IT-005");
    }

    @Test
    void listByEventTypeReturnsOnlyMatchingEventType() {
        // Arrange
        repository.save(entity("MSG-IT-008", "CH0012221716", "DVCA", "ACC-001", "20261231"));
        repository.save(entity("MSG-IT-009", "CH0038863350", "MRGR", "ACC-002", "20261215"));

        // Act
        ResponseEntity<CaSettledEventReadEntity[]> response = rest.getForEntity(
                url("?eventType=DVCA"), CaSettledEventReadEntity[].class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getEventType()).isEqualTo("DVCA");
    }

    @Test
    void settlementRangeReturnsEventsWithinDateRange() {
        // Arrange
        repository.save(entity("MSG-IT-010", "CH0012221716", "DVCA", "ACC-001", "20261101"));
        repository.save(entity("MSG-IT-011", "CH0012255580", "BONU", "ACC-002", "20261215"));
        repository.save(entity("MSG-IT-012", "CH0038863350", "MRGR", "ACC-003", "20270101"));

        // Act
        ResponseEntity<CaSettledEventReadEntity[]> response = rest.getForEntity(
                url("/settlement-range?from=20261201&to=20261231"), CaSettledEventReadEntity[].class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody()[0].getMessageId()).isEqualTo("MSG-IT-011");
    }

    @Test
    void healthReturnsUpStatusWithRowCount() {
        // Arrange
        repository.save(entity("MSG-IT-013", "CH0012221716", "DVCA", "ACC-001", "20261231"));
        repository.save(entity("MSG-IT-014", "CH0038863350", "MRGR", "ACC-002", "20261215"));

        // Act
        ResponseEntity<java.util.Map> response = rest.getForEntity(url("/health"), java.util.Map.class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsEntry("status", "UP");
        assertThat(response.getBody()).containsEntry("service", "ca-confirmations-api");
        assertThat(response.getBody()).containsEntry("master-table-count", "2");
    }

    @Test
    void listNoParamsReturnsAllEvents() {
        // Arrange
        repository.save(entity("MSG-IT-015", "CH0012221716", "DVCA", "ACC-001", "20261231"));
        repository.save(entity("MSG-IT-016", "CH0038863350", "MRGR", "ACC-002", "20261215"));

        // Act
        ResponseEntity<CaSettledEventReadEntity[]> response =
                rest.getForEntity(url(""), CaSettledEventReadEntity[].class);

        // Assert
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(2);
    }
}
