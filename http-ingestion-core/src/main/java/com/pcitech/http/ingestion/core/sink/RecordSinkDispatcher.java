package com.pcitech.http.ingestion.core.sink;

import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RecordSinkDispatcher {

    private final List<RecordSink> sinks;

    public RecordSinkDispatcher(List<RecordSink> sinks) {
        this.sinks = sinks;
    }

    public boolean isAvailable(RuntimeConnectorConfig.SinkSettings settings) {
        if (settings == null) {
            return false;
        }
        return resolve(settings).map(RecordSink::isAvailable).orElse(false);
    }

    public int write(List<Map<String, Object>> records, RuntimeConnectorConfig.SinkSettings settings) throws Exception {
        RecordSink sink = resolve(settings).orElseThrow(() ->
                new IllegalStateException("No sink implementation for type: " + settings.type()));
        if (!sink.isAvailable()) {
            throw new IllegalStateException("Sink " + settings.type() + " is not configured");
        }
        return sink.write(records, settings);
    }

    private java.util.Optional<RecordSink> resolve(RuntimeConnectorConfig.SinkSettings settings) {
        return sinks.stream().filter(s -> s.supports(settings)).findFirst();
    }
}
