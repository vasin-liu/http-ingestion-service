package com.pcitech.http.ingestion.core.service;

import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.dto.IngestionStatsDto;
import com.pcitech.http.ingestion.core.dto.JobRunDto;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class StatsService {

    private final JobRunRepository jobRunRepository;

    public StatsService(JobRunRepository jobRunRepository) {
        this.jobRunRepository = jobRunRepository;
    }

    public IngestionStatsDto getStats(int recentLimit) {
        JobRunRepository.GlobalJobStats global = jobRunRepository.aggregateGlobal();
        List<JobRunDto> recentJobs = jobRunRepository.findRecent(recentLimit).stream()
                .map(this::toDto)
                .toList();
        return new IngestionStatsDto(
                global.totalJobs(),
                global.successJobs(),
                global.failedJobs(),
                global.recordsOk(),
                global.recordsFailed(),
                jobRunRepository.aggregateByConnector(),
                recentJobs
        );
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
}
