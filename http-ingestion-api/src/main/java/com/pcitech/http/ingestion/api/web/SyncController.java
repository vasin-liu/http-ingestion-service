package com.pcitech.http.ingestion.api.web;

import com.pcitech.http.ingestion.core.domain.ConnectorState;
import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.domain.JobRunDetail;
import com.pcitech.http.ingestion.core.dto.ConnectorStateDto;
import com.pcitech.http.ingestion.core.dto.JobRunDetailDto;
import com.pcitech.http.ingestion.core.dto.JobRunDto;
import com.pcitech.http.ingestion.core.dto.TriggerSyncResponseDto;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;
import com.pcitech.http.ingestion.core.service.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/connectors/{id}")
public class SyncController {

    private final SyncService syncService;
    private final JobRunRepository jobRunRepository;

    public SyncController(SyncService syncService, JobRunRepository jobRunRepository) {
        this.syncService = syncService;
        this.jobRunRepository = jobRunRepository;
    }

    @PostMapping("/sync")
    public TriggerSyncResponseDto sync(
            @PathVariable String id,
            @RequestParam(defaultValue = "incremental") String type,
            @RequestParam(required = false) Integer limit,
            @RequestParam(defaultValue = "true") boolean writeSink
    ) {
        SyncService.SyncOptions options = resolveOptions(type, limit, writeSink);
        long jobRunId = syncService.triggerAsync(id, options);
        return new TriggerSyncResponseDto(jobRunId, JobRun.STATUS_PENDING);
    }

    @GetMapping("/state")
    public ConnectorStateDto state(@PathVariable String id) {
        return syncService.getState(id)
                .map(s -> new ConnectorStateDto(s.connectorId(), s.watermarkJson(), s.updatedAt()))
                .orElse(new ConnectorStateDto(id, null, null));
    }

    @PostMapping("/state/reset")
    public void resetState(@PathVariable String id) {
        syncService.resetState(id);
    }

    @GetMapping("/jobs")
    public List<JobRunDto> jobs(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit
    ) {
        int safeLimit = Math.min(Math.max(limit, 1), 200);
        return jobRunRepository.findByConnectorId(id, safeLimit).stream()
                .map(this::toDto)
                .toList();
    }

    @GetMapping("/jobs/{jobId}")
    public JobRunDto job(@PathVariable String id, @PathVariable long jobId) {
        return jobRunRepository.findById(jobId)
                .filter(job -> job.connectorId().equals(id))
                .map(this::toDto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
    }

    @GetMapping("/jobs/{jobId}/details")
    public List<JobRunDetailDto> jobDetails(
            @PathVariable String id,
            @PathVariable long jobId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        JobRun job = jobRunRepository.findById(jobId)
                .filter(j -> j.connectorId().equals(id))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job not found"));
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        return jobRunRepository.findDetailsByJobRunId(job.id(), safeLimit).stream()
                .map(this::toDetailDto)
                .toList();
    }

    private SyncService.SyncOptions resolveOptions(String type, Integer limit, boolean writeSink) {
        if ("full".equalsIgnoreCase(type)) {
            return SyncService.SyncOptions.full();
        }
        if ("sample".equalsIgnoreCase(type)) {
            int sampleLimit = limit == null ? 10 : limit;
            return SyncService.SyncOptions.sample(sampleLimit, writeSink);
        }
        return SyncService.SyncOptions.incremental();
    }

    private JobRunDto toDto(JobRun job) {
        Long durationMs = null;
        if (job.startedAt() != null && job.finishedAt() != null) {
            durationMs = Duration.between(job.startedAt(), job.finishedAt()).toMillis();
        }
        return new JobRunDto(
                job.id(),
                job.connectorId(),
                job.runType(),
                job.status(),
                job.startedAt(),
                job.finishedAt(),
                durationMs,
                job.recordsOk(),
                job.recordsFailed(),
                job.errorMessage()
        );
    }

    private JobRunDetailDto toDetailDto(JobRunDetail detail) {
        return new JobRunDetailDto(
                detail.id(),
                detail.stage(),
                detail.pageNumber(),
                detail.recordIndex(),
                detail.message(),
                detail.sampleJson()
        );
    }
}
