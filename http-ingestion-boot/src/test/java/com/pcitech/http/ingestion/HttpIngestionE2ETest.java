package com.pcitech.http.ingestion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcitech.http.ingestion.api.mock.MockDataCatalog;
import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.dto.JiaduResultInfo;
import com.pcitech.http.ingestion.core.webhook.JiaduSignVerifier;
import com.pcitech.http.ingestion.core.repository.ConnectorScheduleRepository;
import com.pcitech.http.ingestion.core.service.SyncService;
import com.pcitech.http.ingestion.support.AbstractIntegrationE2ETest;
import com.pcitech.http.ingestion.support.ConnectorConfigFactory;
import com.pcitech.http.ingestion.support.E2EJobAwait;
import com.pcitech.http.ingestion.support.PgTestSupport;
import com.pcitech.http.ingestion.support.PullMultiRoundSupport;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.matchers.GroupMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end suite sharing one Spring context and one PostgreSQL Testcontainer.
 */
class HttpIngestionE2ETest extends AbstractIntegrationE2ETest {

    private static final int MOCK_SIZE = MockDataCatalog.DEFAULT_CATALOG_SIZE;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private Scheduler scheduler;

    @RegisterExtension
    static final WireMockExtension WIREMOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @BeforeEach
    void resetEnvironment() throws Exception {
        resetIntegrationEnvironment();
        clearIngestionSchedulerJobs();
    }

    private void clearIngestionSchedulerJobs() throws SchedulerException {
        for (JobKey jobKey : scheduler.getJobKeys(GroupMatcher.jobGroupEquals("ingestion"))) {
            scheduler.deleteJob(jobKey);
        }
    }

    @Nested
    class ConnectorLifecycle {

        @Test
        void crud_publish_templatesAndState() throws Exception {
            JsonNode config = ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl());
            connectorService.create(new ConnectorRequestDto(
                    "lifecycle-connector", "Lifecycle Connector", "pull", config));
            connectorService.publish("lifecycle-connector");

            var listed = restTemplate.getForEntity("/api/connectors", List.class);
            assertThat(listed.getStatusCode().is2xxSuccessful()).isTrue();

            var templates = restTemplate.getForEntity("/api/templates", List.class);
            assertThat(templates.getBody()).isNotNull();
            assertThat(templates.getBody().size()).isEqualTo(6);

            JobRun fullJob = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("lifecycle-connector", SyncService.SyncOptions.full()));
            assertThat(fullJob.status()).isEqualTo(JobRun.STATUS_SUCCESS);

            var reset = restTemplate.postForEntity("/api/connectors/lifecycle-connector/state/reset", null, Void.class);
            assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();

            connectorService.update("lifecycle-connector", new ConnectorRequestDto(
                    "lifecycle-connector", "Lifecycle Connector Updated", "pull", config));
            assertThat(connectorService.get("lifecycle-connector").name()).isEqualTo("Lifecycle Connector Updated");

            connectorService.delete("lifecycle-connector");
            assertThat(restTemplate.getForEntity("/api/connectors/lifecycle-connector", String.class)
                    .getStatusCode().is4xxClientError()).isTrue();
        }

        @Test
        void exportAndImportConnectorConfig() throws Exception {
            JsonNode config = ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl());
            connectorService.create(new ConnectorRequestDto(
                    "export-src", "Export Source", "pull", config));
            connectorService.publish("export-src");

            var exported = restTemplate.getForEntity("/api/connectors/export-src/export", Map.class);
            assertThat(exported.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(exported.getBody()).containsEntry("id", "export-src");

            var imported = restTemplate.postForEntity(
                    "/api/connectors/import",
                    Map.of(
                            "id", "export-dst",
                            "name", "Export Destination",
                            "mode", "pull",
                            "config", exported.getBody().get("config"),
                            "overwrite", false,
                            "publishAfterImport", true
                    ),
                    Map.class
            );
            assertThat(imported.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(imported.getBody()).containsEntry("id", "export-dst");
        }
    }

    @Nested
    class MockSourceApi {

        @Test
        void dahuaAndMeiyaMockEndpoints() {
            var dahuaPage = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/query",
                    Map.of("page", 1, "pageSize", 1, "condition", Map.of()),
                    Map.class
            );
            assertThat(dahuaPage.getBody()).containsEntry("totalCount", MOCK_SIZE);
            assertThat(((List<?>) dahuaPage.getBody().get("results"))).hasSize(1);

            var dahuaCount = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/count",
                    Map.of("condition", Map.of()),
                    Map.class
            );
            assertThat(dahuaCount.getBody()).containsEntry("totalCount", MOCK_SIZE);

            var dahuaIllegalPage = restTemplate.postForEntity(
                    "/mock/dahua/jg/trafficVehicle/illegal/queryList?page=1&pageSize=1",
                    Map.of(),
                    Map.class
            );
            assertThat(dahuaIllegalPage.getBody()).containsEntry("totalCount", MOCK_SIZE);
            assertThat(((List<?>) dahuaIllegalPage.getBody().get("results"))).hasSize(1);

            var dahuaIllegalCount = restTemplate.postForEntity(
                    "/mock/dahua/jg/trafficVehicle/illegal/count",
                    Map.of(),
                    Map.class
            );
            assertThat(dahuaIllegalCount.getBody()).containsEntry("totalCount", MOCK_SIZE);

            var meiyaPolice = restTemplate.postForEntity(
                    "/mock/meiya/api/res/trafficPoliceAlert",
                    Map.of(
                            "params", Map.of("evcc", List.of("2025-06-01 00:02:00", "2025-06-01 00:02:00")),
                            "page", Map.of("skip", 0, "limit", 10)
                    ),
                    Map.class
            );
            assertThat(meiyaStatesTotal(meiyaPolice.getBody())).isEqualTo(1);
        }
    }

    @Nested
    class MockTestApi {

        @Test
        void resetAndAppendDahuaViaHttp() {
            var reset = restTemplate.postForEntity("/mock/_test/reset", null, Map.class);
            assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();

            var afterReset = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/query",
                    Map.of("page", 1, "pageSize", 100, "condition", Map.of()),
                    Map.class
            );
            assertThat(afterReset.getBody()).containsEntry("totalCount", MOCK_SIZE);

            var append = restTemplate.postForEntity(
                    "/mock/_test/dahua/vehicles",
                    Map.of(
                            "recordId", "rec-http-001",
                            "plateNum", "粤D99999",
                            "capTime", "20250601T110000Z",
                            "channelName", "卡口HTTP",
                            "plateType", "02"
                    ),
                    Map.class
            );
            assertThat(append.getStatusCode().is2xxSuccessful()).isTrue();

            var query = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/query",
                    Map.of("page", 1, "pageSize", 100, "condition", Map.of()),
                    Map.class
            );
            assertThat(query.getBody()).containsEntry("totalCount", MOCK_SIZE + 1);
        }

        @Test
        void resetAndAppendMotorIllegalViaHttp() {
            var reset = restTemplate.postForEntity("/mock/_test/reset", null, Map.class);
            assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();

            var append = restTemplate.postForEntity(
                    "/mock/_test/dahua/motor-illegal",
                    Map.of(
                            "recordId", "ill-http-001",
                            "plateNum", "粤D99999",
                            "capTime", Instant.parse("2025-06-01T11:00:00Z").toEpochMilli(),
                            "recType", 1301,
                            "channelName", "违法卡口HTTP",
                            "plateType", "02"
                    ),
                    Map.class
            );
            assertThat(append.getStatusCode().is2xxSuccessful()).isTrue();

            var query = restTemplate.postForEntity(
                    "/mock/dahua/jg/trafficVehicle/illegal/queryList?page=1&pageSize=100",
                    Map.of(),
                    Map.class
            );
            assertThat(query.getBody()).containsEntry("totalCount", MOCK_SIZE + 1);
        }
    }

    @Nested
    class PreviewApi {

        @Test
        void trial_jsonPath_transformPreview_andSampleWithoutSink() throws Exception {
            var trialRequest = Map.of(
                    "method", "POST",
                    "url", baseUrl() + "/mock/dahua/gretrieval/vehicle/query",
                    "body", "{\"page\":1,\"pageSize\":100,\"condition\":{}}",
                    "headers", Map.of("Content-Type", "application/json"),
                    "timeoutMs", 30000
            );
            var trial = restTemplate.postForEntity("/api/trial-requests", trialRequest, Map.class);
            assertThat(trial.getStatusCode().is2xxSuccessful()).isTrue();
            String responseBody = String.valueOf(trial.getBody().get("body"));

            var jsonPaths = restTemplate.postForEntity(
                    "/api/preview/jsonpath",
                    Map.of("responseBody", responseBody, "limit", 10),
                    List.class
            );
            assertThat(jsonPaths.getBody()).isNotEmpty();

            JsonNode config = ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl());
            connectorService.create(new ConnectorRequestDto("sample-no-write", "Sample No Write", "pull", config));
            connectorService.publish("sample-no-write");

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            var sample = restTemplate.postForEntity(
                    "/api/connectors/sample-no-write/sync?type=sample&limit=1&writeSink=false",
                    new HttpEntity<>(headers),
                    Map.class
            );
            long jobRunId = ((Number) sample.getBody().get("jobRunId")).longValue();
            JobRun job = E2EJobAwait.awaitCompletion(jobRunRepository, jobRunId);
            assertThat(job.status()).isEqualTo(JobRun.STATUS_SUCCESS);

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "dahua_vehicle_pass")).isZero();
            }
        }
    }

    @Nested
    class GenericPullSync {

        @BeforeEach
        void stubUsers() {
            WIREMOCK.stubFor(get(urlMatching("/users.*"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("[{\"id\":1,\"name\":\"Alice\"},{\"id\":2,\"name\":\"Bob\"}]")));
        }

        @Test
        void wireMockGet_fullSyncUpsert() throws Exception {
            JsonNode config = ConnectorConfigFactory.genericWireMockUsersConfig(
                    objectMapper,
                    "http://localhost:" + WIREMOCK.getPort()
            );
            connectorService.create(new ConnectorRequestDto("tc-pull-users", "TC Pull Users", "pull", config));
            connectorService.publish("tc-pull-users");

            JobRun job = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("tc-pull-users", SyncService.SyncOptions.full()));
            assertThat(job.status()).isEqualTo(JobRun.STATUS_SUCCESS);
            assertThat(job.recordsOk()).isEqualTo(2);

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "users")).isEqualTo(2);
            }
        }
    }

    @Nested
    class IntegrationSync {

        @Test
        void dahuaVehicleQuery_fullThenIncremental() throws Exception {
            assertFullThenIncremental(
                    "e2e-dahua-vehicle-query",
                    "E2E Dahua Vehicle Query",
                    ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()),
                    "dahua_vehicle_pass",
                    () -> mockStore.addDahuaVehicle(Map.of(
                            "recordId", "rec-incr-001",
                            "plateNum", "粤C11111",
                            "capTime", "20250601T120000Z",
                            "channelName", "卡口C",
                            "plateType", "02"
                    )),
                    MOCK_SIZE,
                    MOCK_SIZE + 1
            );
        }

        @Test
        void dahuaMotorIllegalQuery_fullThenIncremental() throws Exception {
            assertFullThenIncremental(
                    "e2e-dahua-motor-illegal-query",
                    "E2E Dahua Motor Illegal Query",
                    ConnectorConfigFactory.dahuaMotorIllegalQueryConfig(objectMapper, baseUrl()),
                    "dahua_motor_illegal",
                    () -> mockStore.addDahuaMotorIllegal(Map.of(
                            "recordId", "ill-incr-001",
                            "plateNum", "粤C11111",
                            "capTime", Instant.parse("2025-06-01T12:00:00Z").toEpochMilli(),
                            "recType", 1301,
                            "channelName", "违法卡口C",
                            "plateType", "02"
                    )),
                    MOCK_SIZE,
                    MOCK_SIZE + 1
            );
        }

        @Test
        void meiyaTrafficPoliceAlert_fullThenIncremental() throws Exception {
            assertFullThenIncremental(
                    "e2e-meiya-traffic-police",
                    "E2E MeiYa Traffic Police",
                    ConnectorConfigFactory.meiyaTrafficPoliceConfig(objectMapper, baseUrl()),
                    "meiya_traffic_police_alert",
                    () -> {
                        Map<String, Object> extra = new LinkedHashMap<>();
                        extra.put("jqbh", "jq-003");
                        extra.put("jqfssj", "2025-06-01 10:00:00");
                        extra.put("evcc", "2025-06-01 12:00:00");
                        extra.put("desct", "新增警情");
                        mockStore.addMeiyaTrafficPolice(extra);
                    },
                    MOCK_SIZE,
                    MOCK_SIZE + 1
            );
        }

        @Test
        void meiyaDispatch110Flow_fullThenIncremental() throws Exception {
            assertFullThenIncremental(
                    "e2e-meiya-dispatch110",
                    "E2E MeiYa Dispatch110",
                    ConnectorConfigFactory.meiyaDispatch110Config(objectMapper, baseUrl()),
                    "meiya_dispatch110_flow",
                    () -> {
                        Map<String, Object> extra = new LinkedHashMap<>();
                        extra.put("bh", "bh-003");
                        extra.put("jjdbh", "jjd-003");
                        extra.put("gxsj", "2025-06-01 12:06:00");
                        extra.put("xxlxms", "新增流水");
                        mockStore.addMeiyaDispatch110(extra);
                    },
                    MOCK_SIZE,
                    MOCK_SIZE + 1
            );
        }

        private void assertFullThenIncremental(
                String connectorId,
                String name,
                JsonNode config,
                String table,
                Runnable addIncrementalData,
                int initialRows,
                int finalRows
        ) throws Exception {
            publishAndSync(connectorId, name, config, SyncService.SyncOptions.full());
            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, table)).isEqualTo(initialRows);
            }
            addIncrementalData.run();
            JobRun incrementalJob = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync(connectorId, SyncService.SyncOptions.incremental()),
                    240);
            assertThat(incrementalJob.status()).isEqualTo(JobRun.STATUS_SUCCESS);
            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, table)).isEqualTo(finalRows);
            }
        }

        private JobRun publishAndSync(
                String connectorId,
                String name,
                JsonNode config,
                SyncService.SyncOptions options
        ) throws Exception {
            connectorService.create(new ConnectorRequestDto(connectorId, name, "pull", config));
            connectorService.publish(connectorId);
            JobRun job = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync(connectorId, options),
                    240);
            assertThat(job.status()).isEqualTo(JobRun.STATUS_SUCCESS);
            return job;
        }
    }

    @Nested
    class SchedulerSync {

        @Autowired
        private ConnectorScheduleRepository scheduleRepository;

        private JobKey jobKey(String connectorId) {
            return JobKey.jobKey("connector-" + connectorId, "ingestion");
        }

        @Test
        void registersQuartzJobWhenScheduleEnabled() throws Exception {
            ObjectNode config = (ObjectNode) ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()).deepCopy();
            ConnectorConfigFactory.withSchedule(config, true, "0 0/5 * * * ?");

            connectorService.create(new ConnectorRequestDto(
                    "e2e-sched-register", "Scheduler Register", "pull", config));
            connectorService.publish("e2e-sched-register");

            assertThat(scheduler.checkExists(jobKey("e2e-sched-register"))).isTrue();
            assertThat(scheduleRepository.isEnabled("e2e-sched-register")).isTrue();
        }

        @Test
        void unregistersQuartzJobWhenScheduleDisabled() throws Exception {
            ObjectNode enabledConfig = (ObjectNode) ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()).deepCopy();
            ConnectorConfigFactory.withSchedule(enabledConfig, true, "0 0/5 * * * ?");
            connectorService.create(new ConnectorRequestDto(
                    "e2e-sched-disable", "Scheduler Disable", "pull", enabledConfig));
            connectorService.publish("e2e-sched-disable");
            assertThat(scheduler.checkExists(jobKey("e2e-sched-disable"))).isTrue();

            ObjectNode disabledConfig = enabledConfig.deepCopy();
            ConnectorConfigFactory.withSchedule(disabledConfig, false, "0 0/5 * * * ?");
            connectorService.update("e2e-sched-disable", new ConnectorRequestDto(
                    "e2e-sched-disable", "Scheduler Disable", "pull", disabledConfig));
            connectorService.publish("e2e-sched-disable");

            assertThat(scheduler.checkExists(jobKey("e2e-sched-disable"))).isFalse();
            assertThat(scheduleRepository.isEnabled("e2e-sched-disable")).isFalse();
        }

        @Test
        void cronTriggerRunsIncrementalSync() throws Exception {
            ObjectNode config = (ObjectNode) ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()).deepCopy();
            ConnectorConfigFactory.withSchedule(config, false, "0/2 * * * * ?");

            connectorService.create(new ConnectorRequestDto(
                    "e2e-sched-cron", "Scheduler Cron", "pull", config));
            connectorService.publish("e2e-sched-cron");

            JobRun fullJob = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("e2e-sched-cron", SyncService.SyncOptions.full()));
            assertThat(fullJob.status()).isEqualTo(JobRun.STATUS_SUCCESS);

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "dahua_vehicle_pass")).isEqualTo(MOCK_SIZE);
            }

            ObjectNode scheduledConfig = config.deepCopy();
            ConnectorConfigFactory.withSchedule(scheduledConfig, true, "0/2 * * * * ?");
            connectorService.update("e2e-sched-cron", new ConnectorRequestDto(
                    "e2e-sched-cron", "Scheduler Cron", "pull", scheduledConfig));
            connectorService.publish("e2e-sched-cron");
            assertThat(scheduler.checkExists(jobKey("e2e-sched-cron"))).isTrue();

            mockStore.addDahuaVehicle(Map.of(
                    "recordId", "rec-003",
                    "plateNum", "粤C11111",
                    "capTime", "20250601T120000Z",
                    "channelName", "卡口C",
                    "plateType", "02"
            ));

            E2EJobAwait.awaitRowCount(
                    () -> {
                        try (var connection = pgConnection()) {
                            return PgTestSupport.countRows(connection, "dahua_vehicle_pass");
                        }
                    },
                    MOCK_SIZE + 1,
                    90
            );
            assertThat(E2EJobAwait.countSuccessfulJobs(jobRunRepository, "e2e-sched-cron", "incremental"))
                    .isGreaterThanOrEqualTo(1);
        }

        @Test
        void fixedRateTriggerRunsIncrementalSync() throws Exception {
            ObjectNode config = (ObjectNode) ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()).deepCopy();
            ConnectorConfigFactory.withFixedRateSchedule(config, false, 2);

            connectorService.create(new ConnectorRequestDto(
                    "e2e-sched-fixed", "Scheduler Fixed Rate", "pull", config));
            connectorService.publish("e2e-sched-fixed");

            JobRun fullJob = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("e2e-sched-fixed", SyncService.SyncOptions.full()));
            assertThat(fullJob.status()).isEqualTo(JobRun.STATUS_SUCCESS);

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "dahua_vehicle_pass")).isEqualTo(MOCK_SIZE);
            }

            ObjectNode scheduledConfig = config.deepCopy();
            ConnectorConfigFactory.withFixedRateSchedule(scheduledConfig, true, 2);
            connectorService.update("e2e-sched-fixed", new ConnectorRequestDto(
                    "e2e-sched-fixed", "Scheduler Fixed Rate", "pull", scheduledConfig));
            connectorService.publish("e2e-sched-fixed");
            assertThat(scheduler.checkExists(jobKey("e2e-sched-fixed"))).isTrue();

            mockStore.addDahuaVehicle(Map.of(
                    "recordId", "rec-fixed-003",
                    "plateNum", "粤C11111",
                    "capTime", "20250601T120000Z",
                    "channelName", "卡口C",
                    "plateType", "02"
            ));

            E2EJobAwait.awaitRowCount(
                    () -> {
                        try (var connection = pgConnection()) {
                            return PgTestSupport.countRows(connection, "dahua_vehicle_pass");
                        }
                    },
                    MOCK_SIZE + 1,
                    90
            );
        }

        @Test
        void pauseAndResumeSchedule() throws Exception {
            ObjectNode config = (ObjectNode) ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()).deepCopy();
            ConnectorConfigFactory.withSchedule(config, false, "0 0 0 1 1 ? 2099");

            connectorService.create(new ConnectorRequestDto(
                    "e2e-sched-pause", "Scheduler Pause", "pull", config));
            connectorService.publish("e2e-sched-pause");

            JobRun fullJob = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("e2e-sched-pause", SyncService.SyncOptions.full()));
            assertThat(fullJob.status()).isEqualTo(JobRun.STATUS_SUCCESS);

            ObjectNode scheduledConfig = config.deepCopy();
            ConnectorConfigFactory.withSchedule(scheduledConfig, true, "0 0 0 1 1 ? 2099");
            connectorService.update("e2e-sched-pause", new ConnectorRequestDto(
                    "e2e-sched-pause", "Scheduler Pause", "pull", scheduledConfig));
            connectorService.publish("e2e-sched-pause");
            assertThat(scheduler.checkExists(jobKey("e2e-sched-pause"))).isTrue();
            assertThat(scheduleRepository.isEnabled("e2e-sched-pause")).isTrue();

            var pause = restTemplate.postForEntity(
                    "/api/connectors/e2e-sched-pause/schedule/pause", null, Map.class);
            assertThat(pause.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(scheduleRepository.isEnabled("e2e-sched-pause")).isFalse();

            int incrementalJobsAtPause = E2EJobAwait.countSuccessfulJobs(
                    jobRunRepository, "e2e-sched-pause", "incremental");

            String recentCapTime = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                    .withZone(ZoneOffset.UTC)
                    .format(Instant.now());
            mockStore.addDahuaVehicle(Map.of(
                    "recordId", "rec-pause-003",
                    "plateNum", "粤C22222",
                    "capTime", recentCapTime,
                    "channelName", "卡口C",
                    "plateType", "02"
            ));

            Thread.sleep(3000);
            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "dahua_vehicle_pass")).isEqualTo(MOCK_SIZE);
            }
            assertThat(E2EJobAwait.countSuccessfulJobs(jobRunRepository, "e2e-sched-pause", "incremental"))
                    .isEqualTo(incrementalJobsAtPause);

            var resume = restTemplate.postForEntity(
                    "/api/connectors/e2e-sched-pause/schedule/resume", null, Map.class);
            assertThat(resume.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(scheduleRepository.isEnabled("e2e-sched-pause")).isTrue();

            JobRun incrementalJob = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("e2e-sched-pause", SyncService.SyncOptions.incremental()));
            assertThat(incrementalJob.status()).isEqualTo(JobRun.STATUS_SUCCESS);
            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "dahua_vehicle_pass")).isEqualTo(MOCK_SIZE + 1);
            }
        }
    }

    @Nested
    class PullMultiRound {

        @Test
        void dahuaVehicleQuery_r1ThroughR4() throws Exception {
            PullMultiRoundSupport.runR1ToR4(
                    connectorService,
                    syncService,
                    jobRunRepository,
                    restTemplate,
                    () -> countRowsUnchecked("dahua_vehicle_pass"),
                    new PullMultiRoundSupport.Scenario(
                            "mr-dahua-query",
                            "MR Dahua Query",
                            ConnectorConfigFactory.dahuaVehicleQueryConfig(objectMapper, baseUrl()),
                            MOCK_SIZE,
                            () -> mockStore.addDahuaVehicle(dahuaRec("rec-r2", "20250601T120000Z")),
                            () -> mockStore.addDahuaVehicle(dahuaRec("rec-r3", "20250601T130000Z")),
                            MOCK_SIZE + 1,
                            MOCK_SIZE + 2
                    )
            );
        }

        @Test
        void meiyaTrafficPolice_r1ThroughR4() throws Exception {
            PullMultiRoundSupport.runR1ToR4(
                    connectorService,
                    syncService,
                    jobRunRepository,
                    restTemplate,
                    () -> countRowsUnchecked("meiya_traffic_police_alert"),
                    new PullMultiRoundSupport.Scenario(
                            "mr-meiya-traffic",
                            "MR MeiYa Traffic Police",
                            ConnectorConfigFactory.meiyaTrafficPoliceConfig(objectMapper, baseUrl()),
                            MOCK_SIZE,
                            () -> mockStore.addMeiyaTrafficPolice(meiyaTrafficRec("jq-r2", "2025-06-01 12:00:00")),
                            () -> mockStore.addMeiyaTrafficPolice(meiyaTrafficRec("jq-r3", "2025-06-01 13:00:00")),
                            MOCK_SIZE + 1,
                            MOCK_SIZE + 2
                    )
            );
        }

        private static Map<String, Object> dahuaRec(String id, String capTime) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("recordId", id);
            record.put("plateNum", "粤X" + id);
            record.put("capTime", capTime);
            record.put("channelName", "卡口");
            record.put("plateType", "02");
            return record;
        }

        private static Map<String, Object> meiyaTrafficRec(String jqbh, String evcc) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("jqbh", jqbh);
            record.put("jqfssj", evcc);
            record.put("evcc", evcc);
            record.put("desct", "多轮警情");
            return record;
        }

        private int countRowsUnchecked(String table) {
            try (var connection = pgConnection()) {
                return PgTestSupport.countRows(connection, table);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Nested
    class JiaduPushIngress {

        @Test
        void p1_tenEventsPersistWithSuccessOpCode() throws Exception {
            createAndPublishPushConnector("e2e-jiadu-push", false, "ivsp");

            for (int i = 1; i <= 10; i++) {
                JiaduResultInfo result = postIngress("e2e-jiadu-push", jiaduEvent("evt-p1-" + i), null);
                assertThat(result.opCode()).isZero();
            }

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "jiadu_event_info")).isEqualTo(10);
            }
        }

        @Test
        void p2_duplicateEventIdsUpsertAndNewRows() throws Exception {
            createAndPublishPushConnector("e2e-jiadu-dup", false, "ivsp");

            for (int i = 1; i <= 10; i++) {
                assertThat(postIngress("e2e-jiadu-dup", jiaduEvent("evt-dup-" + i), null).opCode()).isZero();
            }
            assertThat(postIngress("e2e-jiadu-dup", jiaduEvent("evt-dup-1"), null).opCode()).isZero();
            assertThat(postIngress("e2e-jiadu-dup", jiaduEvent("evt-dup-2"), null).opCode()).isZero();
            for (int i = 11; i <= 13; i++) {
                assertThat(postIngress("e2e-jiadu-dup", jiaduEvent("evt-dup-" + i), null).opCode()).isZero();
            }

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "jiadu_event_info")).isEqualTo(13);
            }
        }

        @Test
        void p3_badSignRejectedWhenVerifyEnabled() throws Exception {
            createAndPublishPushConnector("e2e-jiadu-sign", true, "ivsp");
            Map<String, Object> event = jiaduEvent("evt-sign-bad");

            JiaduResultInfo result = postIngress("e2e-jiadu-sign", event, "INVALID_SIGN");

            assertThat(result.opCode()).isNotZero();
            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "jiadu_event_info")).isZero();
            }
        }

        @Test
        void p4_imageUrlFieldNormalizedToImgUrlColumn() throws Exception {
            createAndPublishPushConnector("e2e-jiadu-img", false, "ivsp");
            Map<String, Object> event = jiaduEvent("evt-image-url");
            event.remove("ImgUrl");
            event.put("ImageUrl", "http://example/image-url-only.jpg");

            JiaduResultInfo result = postIngress("e2e-jiadu-img", event, null);
            assertThat(result.opCode()).isZero();

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.readString(
                        connection, "jiadu_event_info", "event_id", "evt-image-url", "img_url"
                )).isEqualTo("http://example/image-url-only.jpg");
            }
        }

        @Test
        void mockSimulatorPostsThroughIngress() {
            createAndPublishPushConnectorUnchecked("e2e-jiadu-sim", false, "ivsp");

            var response = restTemplate.postForEntity(
                    "/mock/jiadu/push/e2e-jiadu-sim?rounds=5",
                    null,
                    Map.class
            );
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).containsEntry("success", 5);
            assertThat(countRowsUnchecked("jiadu_event_info")).isEqualTo(5);
        }

        @Test
        void mockSimulatorBulkPush_persistsAtLeast1000Events() {
            createAndPublishPushConnectorUnchecked("e2e-jiadu-bulk", false, "ivsp");

            var response = restTemplate.postForEntity(
                    "/mock/jiadu/push/e2e-jiadu-bulk?rounds=" + MOCK_SIZE + "&idPrefix=bulk",
                    null,
                    Map.class
            );
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            assertThat(response.getBody()).containsEntry("sent", MOCK_SIZE);
            assertThat(response.getBody()).containsEntry("success", MOCK_SIZE);
            assertThat(countRowsUnchecked("jiadu_event_info")).isEqualTo(MOCK_SIZE);
        }

        private void createAndPublishPushConnector(String id, boolean verifySign, String platFlag) throws Exception {
            JsonNode config = ConnectorConfigFactory.jiaduEventPushConfig(objectMapper, verifySign, platFlag);
            connectorService.create(new ConnectorRequestDto(id, "Jiadu Push " + id, "push", config));
            connectorService.publish(id);
        }

        private void createAndPublishPushConnectorUnchecked(String id, boolean verifySign, String platFlag) {
            try {
                createAndPublishPushConnector(id, verifySign, platFlag);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }

        private JiaduResultInfo postIngress(String connectorId, Map<String, Object> event, String sign) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x_request_id", String.valueOf(event.get("EventID")));
            if (sign != null) {
                headers.set("sign", sign);
            } else {
                String eventId = String.valueOf(event.get("EventID"));
                headers.set("sign", JiaduSignVerifier.compute("ivsp", eventId));
            }
            var response = restTemplate.postForEntity(
                    "/ingress/" + connectorId,
                    new HttpEntity<>(event, headers),
                    JiaduResultInfo.class
            );
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
            return response.getBody();
        }

        private static Map<String, Object> jiaduEvent(String eventId) {
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("EventID", eventId);
            event.put("EventType", 3001);
            event.put("EventName", "测试事件");
            event.put("SendTime", "2025-06-01 10:00:00");
            event.put("CameraID", "44010000001320000001");
            event.put("ImgUrl", "http://example/alarm.jpg");
            event.put("EventTime", "2025-06-01 10:00:00");
            event.put("Confidence", 0.95);
            event.put("TaskID", "TASK-001");
            event.put("EventGroup", 1);
            event.put("Census", 1);
            event.put("InterDay", 0);
            event.put("EnterNumber", 0);
            event.put("OutNumber", 0);
            return event;
        }

        private int countRowsUnchecked(String table) {
            try (var connection = pgConnection()) {
                return PgTestSupport.countRows(connection, table);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Nested
    class MockBulkBoundary {

        @Test
        void mockCatalog_hasAtLeast1000RecordsPerIntegrationSource() {
            assertThat(mockStore.dahuaVehicleCount()).isGreaterThanOrEqualTo(MOCK_SIZE);
            assertThat(mockStore.dahuaMotorIllegalCount()).isGreaterThanOrEqualTo(MOCK_SIZE);
            assertThat(mockStore.meiyaTrafficPoliceCount()).isGreaterThanOrEqualTo(MOCK_SIZE);
            assertThat(mockStore.meiyaDispatch110Count()).isGreaterThanOrEqualTo(MOCK_SIZE);
        }

        @Test
        void dahuaVehicle_separateCountMatchesPagedQuery() {
            var count = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/count",
                    Map.of("condition", Map.of()),
                    Map.class
            );
            assertThat(count.getBody()).containsEntry("totalCount", MOCK_SIZE);

            var firstPage = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/query",
                    Map.of("page", 1, "pageSize", 100, "condition", Map.of()),
                    Map.class
            );
            assertThat(firstPage.getBody()).containsEntry("totalCount", MOCK_SIZE);
            assertThat(((List<?>) firstPage.getBody().get("results"))).hasSize(100);

            var lastPage = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/query",
                    Map.of("page", 10, "pageSize", 100, "condition", Map.of()),
                    Map.class
            );
            assertThat(((List<?>) lastPage.getBody().get("results"))).hasSize(100);

            var beyond = restTemplate.postForEntity(
                    "/mock/dahua/gretrieval/vehicle/query",
                    Map.of("page", 11, "pageSize", 100, "condition", Map.of()),
                    Map.class
            );
            assertThat(((List<?>) beyond.getBody().get("results"))).isEmpty();
        }

        @Test
        void dahuaMotorIllegal_queryPaginationBoundaries() {
            var page = restTemplate.postForEntity(
                    "/mock/dahua/jg/trafficVehicle/illegal/queryList?page=1&pageSize=500",
                    Map.of(),
                    Map.class
            );
            assertThat(page.getBody()).containsEntry("totalCount", MOCK_SIZE);
            assertThat(((List<?>) page.getBody().get("results"))).hasSize(500);
            assertThat(page.getBody()).containsEntry("nextPage", 2);
        }

        @Test
        void meiyaTrafficPolice_skipLimitBoundary() {
            var first = restTemplate.postForEntity(
                    "/mock/meiya/api/res/trafficPoliceAlert",
                    Map.of("params", Map.of(), "page", Map.of("skip", 0, "limit", 500)),
                    Map.class
            );
            assertThat(meiyaStatesTotal(first.getBody())).isEqualTo(MOCK_SIZE);
            assertThat(((List<?>) first.getBody().get("data"))).hasSize(500);
        }
    }

    @Nested
    class OffsetLimitPull {

        @BeforeEach
        void stubPagedItems() {
            WIREMOCK.resetAll();
            WIREMOCK.stubFor(get(urlMatching("/items\\?offset=0&limit=2"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("[{\"id\":1,\"name\":\"A\"},{\"id\":2,\"name\":\"B\"}]")));
            WIREMOCK.stubFor(get(urlMatching("/items\\?offset=2&limit=2"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("[{\"id\":3,\"name\":\"C\"},{\"id\":4,\"name\":\"D\"}]")));
            WIREMOCK.stubFor(get(urlMatching("/items\\?offset=4&limit=2"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("[{\"id\":5,\"name\":\"E\"},{\"id\":6,\"name\":\"F\"}]")));
            WIREMOCK.stubFor(get(urlMatching("/items\\?offset=6&limit=2"))
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("[]")));
        }

        @Test
        void wireMockOffsetLimit_threePagesSixRows() throws Exception {
            JsonNode config = ConnectorConfigFactory.offsetLimitItemsConfig(
                    objectMapper,
                    "http://localhost:" + WIREMOCK.getPort()
            );
            connectorService.create(new ConnectorRequestDto("e2e-offset-limit", "Offset Limit Items", "pull", config));
            connectorService.publish("e2e-offset-limit");

            JobRun job = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("e2e-offset-limit", SyncService.SyncOptions.full()));
            assertThat(job.status()).isEqualTo(JobRun.STATUS_SUCCESS);
            assertThat(job.recordsOk()).isEqualTo(6);

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "users")).isEqualTo(6);
            }
        }
    }

    @Nested
    class CursorPull {

        @BeforeEach
        void stubCursorItems() {
            WIREMOCK.resetAll();
            WIREMOCK.stubFor(get(urlPathEqualTo("/items"))
                    .withQueryParam("cursor", equalTo("c2"))
                    .atPriority(1)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"data":[{"id":3,"name":"C"}],"meta":{"next":"","hasMore":false}}
                                    """)));
            WIREMOCK.stubFor(get(urlPathEqualTo("/items"))
                    .atPriority(2)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"data":[{"id":1,"name":"A"},{"id":2,"name":"B"}],"meta":{"next":"c2","hasMore":true}}
                                    """)));
        }

        @Test
        void wireMockCursor_twoPagesThreeRows() throws Exception {
            JsonNode config = ConnectorConfigFactory.cursorItemsConfig(
                    objectMapper,
                    "http://localhost:" + WIREMOCK.getPort()
            );
            connectorService.create(new ConnectorRequestDto("e2e-cursor", "Cursor Items", "pull", config));
            connectorService.publish("e2e-cursor");

            JobRun job = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("e2e-cursor", SyncService.SyncOptions.full()));
            assertThat(job.status()).as("job error: %s", job.errorMessage()).isEqualTo(JobRun.STATUS_SUCCESS);
            assertThat(job.recordsOk()).isEqualTo(3);

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "users")).isEqualTo(3);
            }
        }
    }

    @Nested
    class LinkHeaderPull {

        @BeforeEach
        void stubLinkItems() {
            WIREMOCK.resetAll();
            int port = WIREMOCK.getPort();
            String page2Url = "http://localhost:" + port + "/items?page=2";
            WIREMOCK.stubFor(get(urlPathEqualTo("/items"))
                    .withQueryParam("page", equalTo("2"))
                    .atPriority(1)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"data":[{"id":3,"name":"C"}]}
                                    """)));
            WIREMOCK.stubFor(get(urlPathEqualTo("/items"))
                    .atPriority(2)
                    .willReturn(aResponse()
                            .withHeader("Content-Type", "application/json")
                            .withHeader("Link", "<" + page2Url + ">; rel=\"next\"")
                            .withBody("""
                                    {"data":[{"id":1,"name":"A"},{"id":2,"name":"B"}]}
                                    """)));
        }

        @Test
        void wireMockLinkHeader_twoPagesThreeRows() throws Exception {
            JsonNode config = ConnectorConfigFactory.linkHeaderItemsConfig(
                    objectMapper,
                    "http://localhost:" + WIREMOCK.getPort()
            );
            connectorService.create(new ConnectorRequestDto("e2e-link-header", "Link Header Items", "pull", config));
            connectorService.publish("e2e-link-header");

            JobRun job = E2EJobAwait.awaitCompletion(
                    jobRunRepository,
                    syncService.triggerAsync("e2e-link-header", SyncService.SyncOptions.full()));
            assertThat(job.status()).as("job error: %s", job.errorMessage()).isEqualTo(JobRun.STATUS_SUCCESS);
            assertThat(job.recordsOk()).isEqualTo(3);

            try (var connection = pgConnection()) {
                assertThat(PgTestSupport.countRows(connection, "users")).isEqualTo(3);
            }
        }
    }

    private static int meiyaStatesTotal(Map<?, ?> body) {
        Object total = ((Map<?, ?>) body.get("states")).get("total");
        return ((Number) total).intValue();
    }
}
