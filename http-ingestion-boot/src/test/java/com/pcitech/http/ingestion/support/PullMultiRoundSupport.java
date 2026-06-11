package com.pcitech.http.ingestion.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;
import com.pcitech.http.ingestion.core.service.ConnectorService;
import com.pcitech.http.ingestion.core.service.SyncService;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.function.IntSupplier;

import static org.assertj.core.api.Assertions.assertThat;

public final class PullMultiRoundSupport {

    private PullMultiRoundSupport() {
    }

    public record Scenario(
            String connectorId,
            String name,
            JsonNode config,
            int initialRows,
            Runnable appendRound2,
            Runnable appendRound3,
            int afterRound2Rows,
            int afterRound3Rows
    ) {
    }

    public static void runR1ToR4(
            ConnectorService connectorService,
            SyncService syncService,
            JobRunRepository jobRunRepository,
            TestRestTemplate restTemplate,
            IntSupplier pgRowCount,
            Scenario scenario
    ) throws Exception {
        connectorService.create(new ConnectorRequestDto(
                scenario.connectorId(), scenario.name(), "pull", scenario.config()));
        connectorService.publish(scenario.connectorId());

        JobRun r1 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.full()));
        assertThat(r1.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.initialRows());

        scenario.appendRound2().run();
        JobRun r2 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.incremental()));
        assertThat(r2.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.afterRound2Rows());

        scenario.appendRound3().run();
        JobRun r3 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.incremental()));
        assertThat(r3.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.afterRound3Rows());

        var reset = restTemplate.postForEntity(
                "/api/connectors/" + scenario.connectorId() + "/state/reset", null, Void.class);
        assertThat(reset.getStatusCode().is2xxSuccessful()).isTrue();

        JobRun r4 = E2EJobAwait.awaitCompletion(
                jobRunRepository,
                syncService.triggerAsync(scenario.connectorId(), SyncService.SyncOptions.full()));
        assertThat(r4.status()).isEqualTo(JobRun.STATUS_SUCCESS);
        assertThat(pgRowCount.getAsInt()).isEqualTo(scenario.afterRound3Rows());
    }
}
