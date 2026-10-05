package com.insurer.claimflow.it;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Base class for integration tests: one PostgreSQL and one Kafka container shared by all test classes
 * (singleton container pattern) so that the Spring context cache can be reused across classes.
 * Tests are skipped – not failed – when Docker is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = "claimflow.demo-seed.enabled=false")
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("claimflow")
            .withUsername("claimflow")
            .withPassword("claimflow");

    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:3.8.0");

    @Autowired
    protected MockMvc mvc;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        Startables.deepStart(POSTGRES, KAFKA).join();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("claimflow.outbox.poll-interval", () -> "100ms");
    }
}
