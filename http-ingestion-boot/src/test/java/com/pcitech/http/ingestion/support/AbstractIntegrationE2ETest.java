package com.pcitech.http.ingestion.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.api.mock.MockIntegrationSourceStore;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;
import com.pcitech.http.ingestion.core.service.ConnectorService;
import com.pcitech.http.ingestion.core.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("e2e")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationE2ETest {

    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @LocalServerPort
    protected int port;

    @Autowired
    protected ConnectorService connectorService;

    @Autowired
    protected SyncService syncService;

    @Autowired
    protected JobRunRepository jobRunRepository;

    @Autowired
    protected MockIntegrationSourceStore mockStore;

    @Autowired
    protected ObjectMapper objectMapper;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",
                () -> "jdbc:h2:mem:integration-e2e;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        registry.add("ingestion.external-pg.url", POSTGRES::getJdbcUrl);
        registry.add("ingestion.external-pg.username", POSTGRES::getUsername);
        registry.add("ingestion.external-pg.password", POSTGRES::getPassword);
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }

    protected void resetIntegrationEnvironment() throws Exception {
        mockStore.reset();
        PgTestSupport.createIntegrationTables(POSTGRES.createConnection(""));
    }

    protected java.sql.Connection pgConnection() throws Exception {
        return POSTGRES.createConnection("");
    }
}
