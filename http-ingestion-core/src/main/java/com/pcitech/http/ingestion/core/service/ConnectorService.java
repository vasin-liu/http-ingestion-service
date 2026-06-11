package com.pcitech.http.ingestion.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pcitech.http.ingestion.core.domain.Connector;
import com.pcitech.http.ingestion.core.domain.ConnectorVersion;
import com.pcitech.http.ingestion.core.dto.ConnectorExportDto;
import com.pcitech.http.ingestion.core.dto.ConnectorImportRequestDto;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.dto.ConnectorResponseDto;
import com.pcitech.http.ingestion.core.dto.ConnectorSummaryDto;
import com.pcitech.http.ingestion.core.event.ConnectorPublishedEvent;
import com.pcitech.http.ingestion.core.repository.ConnectorRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ConnectorService {

    private final ConnectorRepository repository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ConnectorService(
            ConnectorRepository repository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public List<ConnectorSummaryDto> list() {
        return repository.findAllSummaries().stream()
                .map(s -> new ConnectorSummaryDto(
                        s.id(), s.name(), s.mode(), s.hasDraft(),
                        s.latestPublishedVersion(), s.updatedAt()))
                .toList();
    }

    public ConnectorResponseDto get(String id) {
        Connector connector = requireConnector(id);
        JsonNode draftConfig = repository.findDraftVersion(id)
                .map(v -> parseConfig(v.configJson()))
                .orElse(objectMapper.createObjectNode());
        Integer latestPublished = repository.findLatestPublished(id)
                .map(ConnectorVersion::versionNumber)
                .orElse(null);
        return toResponse(connector, draftConfig, latestPublished);
    }

    @Transactional
    public ConnectorResponseDto create(ConnectorRequestDto request) {
        if (repository.existsById(request.id())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Connector already exists: " + request.id());
        }
        String configJson = toConfigJson(request.config(), request.mode());
        repository.insertConnector(request.id(), request.name(), request.mode());
        repository.insertDraftVersion(request.id(), configJson);
        return get(request.id());
    }

    @Transactional
    public ConnectorResponseDto update(String id, ConnectorRequestDto request) {
        requireConnector(id);
        if (!id.equals(request.id())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Path id does not match body id");
        }
        String configJson = toConfigJson(request.config(), request.mode());
        repository.updateConnector(id, request.name(), request.mode());
        repository.updateDraftConfig(id, configJson);
        return get(id);
    }

    @Transactional
    public void delete(String id) {
        requireConnector(id);
        repository.deleteConnector(id);
    }

    @Transactional
    public ConnectorResponseDto publish(String id) {
        requireConnector(id);
        ConnectorVersion draft = repository.findDraftVersion(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No draft to publish"));
        int versionNumber = repository.nextPublishedVersionNumber(id);
        repository.publishDraft(id, versionNumber);
        repository.insertDraftFromPublished(id, draft.configJson());
        eventPublisher.publishEvent(new ConnectorPublishedEvent(id, draft.configJson()));
        return get(id);
    }

    public ConnectorExportDto export(String id) {
        ConnectorResponseDto connector = get(id);
        return new ConnectorExportDto(
                "1.0",
                connector.id(),
                connector.name(),
                connector.mode(),
                connector.draftConfig(),
                connector.latestPublishedVersion(),
                java.time.Instant.now()
        );
    }

    @Transactional
    public ConnectorResponseDto importConnector(ConnectorImportRequestDto request) {
        ConnectorRequestDto connectorRequest = new ConnectorRequestDto(
                request.id(),
                request.name(),
                request.mode(),
                request.config()
        );
        ConnectorResponseDto result;
        if (repository.existsById(request.id())) {
            if (!request.overwrite()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Connector already exists: " + request.id()
                );
            }
            result = update(request.id(), connectorRequest);
        } else {
            result = create(connectorRequest);
        }
        if (request.publishAfterImport()) {
            result = publish(request.id());
        }
        return result;
    }

    private Connector requireConnector(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Connector not found: " + id));
    }

    private ConnectorResponseDto toResponse(Connector connector, JsonNode draftConfig, Integer latestPublished) {
        return new ConnectorResponseDto(
                connector.id(),
                connector.name(),
                connector.mode(),
                draftConfig,
                latestPublished,
                connector.createdAt(),
                connector.updatedAt()
        );
    }

    private String toConfigJson(JsonNode config, String mode) {
        try {
            ObjectNode root = config == null || !config.isObject()
                    ? objectMapper.createObjectNode()
                    : (ObjectNode) config.deepCopy();
            root.put("mode", mode);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid config JSON");
        }
    }

    private JsonNode parseConfig(String configJson) {
        try {
            return objectMapper.readTree(configJson);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}
