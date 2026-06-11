package com.pcitech.http.ingestion.core.event;

public record ConnectorPublishedEvent(String connectorId, String configJson) {
}
