package com.pcitech.http.ingestion.support;

import com.pcitech.http.ingestion.core.domain.JobRun;
import com.pcitech.http.ingestion.core.repository.JobRunRepository;

import java.util.List;

public final class E2EJobAwait {

    private E2EJobAwait() {
    }

    public static JobRun awaitCompletion(JobRunRepository jobRunRepository, long jobRunId)
            throws InterruptedException {
        for (int attempt = 0; attempt < 60; attempt++) {
            JobRun job = jobRunRepository.findById(jobRunId).orElseThrow();
            if (!JobRun.STATUS_PENDING.equals(job.status()) && !JobRun.STATUS_RUNNING.equals(job.status())) {
                return job;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for job " + jobRunId);
    }

    public static JobRun awaitCompletion(JobRunRepository jobRunRepository, long jobRunId, int maxAttempts)
            throws InterruptedException {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            JobRun job = jobRunRepository.findById(jobRunId).orElseThrow();
            if (!JobRun.STATUS_PENDING.equals(job.status()) && !JobRun.STATUS_RUNNING.equals(job.status())) {
                return job;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for job " + jobRunId);
    }

    public static int countSuccessfulJobs(JobRunRepository jobRunRepository, String connectorId, String runType) {
        return (int) jobRunRepository.findByConnectorId(connectorId, 50).stream()
                .filter(job -> runType.equals(job.runType()))
                .filter(job -> JobRun.STATUS_SUCCESS.equals(job.status()))
                .count();
    }

    public static void awaitNoRunningJobs(
            JobRunRepository jobRunRepository,
            String connectorId,
            String runType,
            int maxAttempts
    ) throws InterruptedException {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            boolean running = jobRunRepository.findByConnectorId(connectorId, 50).stream()
                    .filter(job -> runType.equals(job.runType()))
                    .anyMatch(job -> JobRun.STATUS_PENDING.equals(job.status())
                            || JobRun.STATUS_RUNNING.equals(job.status()));
            if (!running) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for no running " + runType + " jobs for " + connectorId);
    }

    public static void awaitRowCount(RowCountProbe probe, int expectedRows, int maxAttempts)
            throws Exception {
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            if (probe.count() >= expectedRows) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("Timed out waiting for row count >= " + expectedRows + ", last=" + probe.count());
    }

    @FunctionalInterface
    public interface RowCountProbe {
        int count() throws Exception;
    }
}
