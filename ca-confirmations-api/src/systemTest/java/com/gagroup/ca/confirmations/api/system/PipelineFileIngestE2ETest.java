package com.gagroup.ca.confirmations.api.system;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * E2E Entry Point B — File Ingest.
 * Posts an MT566 confirmation via ca-producer REST endpoint.
 * Tests the FULL SYSTEM including the COBOL batch adapter (ca-producer).
 * This mirrors the real-world flow: COBOL batch job writes file,
 * adapter reads and publishes to Kafka, pipeline processes to master table.
 */
@Testcontainers
class PipelineFileIngestE2ETest {

    static final Network NETWORK = Network.newNetwork();

    @Container
    static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka")
                    .withListener(() -> "kafka:19092");

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withNetwork(NETWORK)
                    .withNetworkAliases("postgres")
                    .withDatabaseName("caevents")
                    .withUsername("causer")
                    .withPassword("capass")
                    .withInitScript("init.sql");

    @Container
    static final GenericContainer<?> COBOL_STUB =
            new GenericContainer<>("ca-cobol-stub:0.0.1-SNAPSHOT")
                    .withNetwork(NETWORK)
                    .withNetworkAliases("cobol-stub")
                    .withLogConsumer(logConsumer("cobol-stub"))
                    .withExposedPorts(8086)
                    .waitingFor(Wait.forHttp("/actuator/health")
                            .withStartupTimeout(Duration.ofSeconds(60)));

    @Container
    static final GenericContainer<?> PRODUCER =
            new GenericContainer<>("ca-producer:0.0.1-SNAPSHOT")
                    .withNetwork(NETWORK)
                    .dependsOn(KAFKA)
                    .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
                    .withLogConsumer(logConsumer("producer"))
                    .withExposedPorts(8081)
                    .waitingFor(Wait.forHttp("/actuator/health")
                            .withStartupTimeout(Duration.ofSeconds(90)));

    @Container
    static final GenericContainer<?> FORMATTER =
            new GenericContainer<>("ca-formatter:0.0.1-SNAPSHOT")
                    .withNetwork(NETWORK)
                    .dependsOn(KAFKA)
                    .withEnv("SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:19092")
                    .withLogConsumer(logConsumer("formatter"))
                    .withExposedPorts(8082)
                    .waitingFor(Wait.forHttp("/actuator/health")
                            .withStartupTimeout(Duration.ofSeconds(90)));

    @Container
    static final GenericContainer<?> ENRICHER =
            new GenericContainer<>("ca-enricher:0.0.1-SNAPSHOT")
                    .withNetwork(NETWORK)
                    .dependsOn(KAFKA, COBOL_STUB, POSTGRES)
                    .withEnv(Map.of(
                            "SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:19092",
                            "COBOL_STUB_URL",                 "http://cobol-stub:8086",
                            "SPRING_DATASOURCE_URL",          "jdbc:postgresql://postgres:5432/caevents",
                            "SPRING_DATASOURCE_USERNAME",     "causer",
                            "SPRING_DATASOURCE_PASSWORD",     "capass"))
                    .withLogConsumer(logConsumer("enricher"))
                    .withExposedPorts(8083)
                    .waitingFor(Wait.forHttp("/actuator/health")
                            .withStartupTimeout(Duration.ofSeconds(90)));

    @Container
    static final GenericContainer<?> MATERIALIZER =
            new GenericContainer<>("ca-materializer:0.0.1-SNAPSHOT")
                    .withNetwork(NETWORK)
                    .dependsOn(KAFKA, POSTGRES)
                    .withEnv(Map.of(
                            "SPRING_KAFKA_BOOTSTRAP_SERVERS", "kafka:19092",
                            "SPRING_DATASOURCE_URL",          "jdbc:postgresql://postgres:5432/caevents",
                            "SPRING_DATASOURCE_USERNAME",     "causer",
                            "SPRING_DATASOURCE_PASSWORD",     "capass"))
                    .withLogConsumer(logConsumer("materializer"))
                    .withExposedPorts(8084)
                    .waitingFor(Wait.forHttp("/actuator/health")
                            .withStartupTimeout(Duration.ofSeconds(90)));

    @Container
    static final GenericContainer<?> API =
            new GenericContainer<>("ca-confirmations-api:0.0.1-SNAPSHOT")
                    .withNetwork(NETWORK)
                    .dependsOn(POSTGRES)
                    .withEnv(Map.of(
                            "SPRING_DATASOURCE_URL",      "jdbc:postgresql://postgres:5432/caevents",
                            "SPRING_DATASOURCE_USERNAME", "causer",
                            "SPRING_DATASOURCE_PASSWORD", "capass"))
                    .withLogConsumer(logConsumer("api"))
                    .withExposedPorts(8085)
                    .waitingFor(Wait.forHttp("/actuator/health")
                            .withStartupTimeout(Duration.ofSeconds(90)));

    private final RestTemplate rest = new RestTemplate();

    @Test
    @DisplayName("Entry Point B — File Ingest: COBOL batch MT566 file POST " +
                 "flows through full system to ca-confirmations-api")
    void fileIngestMt566ConfirmationShouldAppearInMasterTableViaApi() {
        // Arrange
        String mt566   = "CONF-20261231-002|CH0038863350|DVCA|20261231|850.00|CHF|ACC-002|500|SETT";
        var    headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        var    request    = new HttpEntity<>(mt566, headers);
        String producerUrl = producerBaseUrl() + "/api/v1/ingest/mt566";

        // Act — POST raw MT566 line as a COBOL batch job would
        ResponseEntity<Map> publishResp = rest.exchange(
                producerUrl, HttpMethod.POST, request, Map.class);
        String messageId = (String) publishResp.getBody().get("messageId");

        // Assert — ingest accepted and messageId returned
        assertThat(publishResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(messageId).isNotBlank();

        // Assert — event propagates through pipeline and appears in API within 30s
        String apiUrl = apiBaseUrl() + "/api/v1/settled-confirmations/" + messageId;
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    ResponseEntity<Map> apiResp = rest.getForEntity(apiUrl, Map.class);
                    assertThat(apiResp.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(apiResp.getBody().get("isin")).isEqualTo("CH0038863350");
                    assertThat(apiResp.getBody().get("securityName")).isEqualTo("Trillian Astra PLC");
                    assertThat(apiResp.getBody().get("eventType")).isEqualTo("DVCA");
                });
    }

    String producerBaseUrl() {
        return "http://" + PRODUCER.getHost() + ":" + PRODUCER.getMappedPort(8081);
    }

    String apiBaseUrl() {
        return "http://" + API.getHost() + ":" + API.getMappedPort(8085);
    }

    private static Slf4jLogConsumer logConsumer(String name) {
        return new Slf4jLogConsumer(LoggerFactory.getLogger("systemTest." + name)).withPrefix(name);
    }
}
