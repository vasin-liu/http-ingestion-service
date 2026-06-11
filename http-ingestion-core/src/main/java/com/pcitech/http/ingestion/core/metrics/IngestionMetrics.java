package com.pcitech.http.ingestion.core.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class IngestionMetrics {

    private final MeterRegistry registry;
    private final ConcurrentMap<String, AtomicReference<Double>> watermarkLagGauges = new ConcurrentHashMap<>();

    public IngestionMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.registry = meterRegistryProvider.getIfAvailable();
    }

    public void recordJobComplete(
            String connectorId,
            String runType,
            String status,
            Duration duration,
            int recordsOk,
            int recordsFailed
    ) {
        if (registry == null) {
            return;
        }
        Tags base = Tags.of("connector_id", connectorId);
        registry.counter("ingestion_job_total", base.and("status", status)).increment();
        registry.timer("ingestion_job_duration_seconds", base.and("type", runType))
                .record(duration);
        if (recordsOk > 0) {
            registry.counter("ingestion_records_processed_total",
                    base.and("stage", "transform")).increment(recordsOk);
        }
        if (recordsFailed > 0) {
            registry.counter("ingestion_records_processed_total",
                    base.and("stage", "failed")).increment(recordsFailed);
        }
    }

    public void updateWatermarkLag(String connectorId, Instant watermark) {
        if (registry == null) {
            return;
        }
        double lagSeconds = watermark == null
                ? 0.0
                : Math.max(0, Duration.between(watermark, Instant.now()).getSeconds());
        AtomicReference<Double> holder = watermarkLagGauges.computeIfAbsent(connectorId, id -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            registry.gauge("ingestion_watermark_lag_seconds",
                    Tags.of("connector_id", id),
                    ref,
                    r -> r.get() == null ? 0.0 : r.get());
            return ref;
        });
        holder.set(lagSeconds);
    }
}
