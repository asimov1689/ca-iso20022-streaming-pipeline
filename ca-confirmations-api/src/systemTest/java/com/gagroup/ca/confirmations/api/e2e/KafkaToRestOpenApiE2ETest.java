package com.gagroup.ca.confirmations.api.e2e;

import com.gagroup.ca.model.RawConfirmationEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.serializer.JsonSerializer;
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
import java.time.Instant;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
public class KafkaToRestOpenApiE2ETest {

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
    static final GenericContainer<?> FORMATTER =
            new GenericContainer<>("ca-formatter:0.0.1-SNAPSHOT")
                    .withNetwork(NETWORK)
                    .dependsOn(KAFKA)
                    .withEnv(Map.of(
                            "SPRING_KAFKA_BOOTSTRAP_SERVERS",         "kafka:19092",
                            "SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET", "earliest"))
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
                            "SPRING_KAFKA_BOOTSTRAP_SERVERS",          "kafka:19092",
                            "SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET", "earliest",
                            "COBOL_STUB_URL",                          "http://cobol-stub:8086",
                            "SPRING_DATASOURCE_URL",                   "jdbc:postgresql://postgres:5432/caevents",
                            "SPRING_DATASOURCE_USERNAME",              "causer",
                            "SPRING_DATASOURCE_PASSWORD",              "capass"))
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
                            "SPRING_KAFKA_BOOTSTRAP_SERVERS",          "kafka:19092",
                            "SPRING_KAFKA_CONSUMER_AUTO_OFFSET_RESET", "earliest",
                            "SPRING_DATASOURCE_URL",                   "jdbc:postgresql://postgres:5432/caevents",
                            "SPRING_DATASOURCE_USERNAME",              "causer",
                            "SPRING_DATASOURCE_PASSWORD",              "capass"))
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
    @DisplayName("Kafka to REST E2E: raw confirmation becomes queryable through OpenAPI-backed API")
    void kafkaMessageShouldReachRestApiAndExposeOpenApiContract() throws Exception {
        String messageId = UUID.randomUUID().toString();
        String mt566 = "CONF-20261231-001|CH0012221716|DVCA|20261231|2500.00|CHF|ACC-001|1000|SETT";
        var rawEvent = new RawConfirmationEvent(messageId, "MT566", mt566, Instant.now());
        var producer = buildKafkaProducer();

        producer.send(new ProducerRecord<>("ca.confirmations.raw", messageId, rawEvent)).get(10, TimeUnit.SECONDS);
        producer.flush();
        producer.close();

        String eventUrl = apiBaseUrl() + "/api/v1/settled-confirmations/" + messageId;
        await().atMost(30, TimeUnit.SECONDS).pollInterval(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    ResponseEntity<Map> response = rest.getForEntity(eventUrl, Map.class);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    assertThat(response.getBody().get("isin")).isEqualTo("CH0012221716");
                    assertThat(response.getBody().get("eventType")).isEqualTo("DVCA");
                    assertThat(response.getBody().get("securityName")).isEqualTo("Arthur Dent Holdings");
                    assertThat(response.getBody().get("confirmationRef")).isEqualTo("CONF-20261231-001");
                });

        ResponseEntity<Map> openApi = rest.getForEntity(apiBaseUrl() + "/v3/api-docs", Map.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody().get("openapi")).asString().startsWith("3.");
        Map<String, Object> paths = (Map<String, Object>) openApi.getBody().get("paths");
        assertThat(paths).containsKey("/api/v1/settled-confirmations/{messageId}");
    }

    private KafkaProducer<String, RawConfirmationEvent> buildKafkaProducer() {
        var props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new KafkaProducer<>(props);
    }

    String apiBaseUrl() {
        return "http://" + API.getHost() + ":" + API.getMappedPort(8085);
    }

    private static Slf4jLogConsumer logConsumer(String name) {
        return new Slf4jLogConsumer(LoggerFactory.getLogger("systemTest." + name)).withPrefix(name);
    }
}
