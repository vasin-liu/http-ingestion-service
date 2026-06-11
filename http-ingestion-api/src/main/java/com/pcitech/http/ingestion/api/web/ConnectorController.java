package com.pcitech.http.ingestion.api.web;

import com.pcitech.http.ingestion.core.dto.ConnectorExportDto;
import com.pcitech.http.ingestion.core.dto.ConnectorImportRequestDto;
import com.pcitech.http.ingestion.core.dto.ConnectorRequestDto;
import com.pcitech.http.ingestion.core.dto.ConnectorResponseDto;
import com.pcitech.http.ingestion.core.dto.ConnectorSummaryDto;
import com.pcitech.http.ingestion.core.service.ConnectorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/connectors")
public class ConnectorController {

    private final ConnectorService connectorService;

    public ConnectorController(ConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping
    public List<ConnectorSummaryDto> list() {
        return connectorService.list();
    }

    @GetMapping("/{id}")
    public ConnectorResponseDto get(@PathVariable String id) {
        return connectorService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectorResponseDto create(@Valid @RequestBody ConnectorRequestDto request) {
        return connectorService.create(request);
    }

    @PutMapping("/{id}")
    public ConnectorResponseDto update(@PathVariable String id, @Valid @RequestBody ConnectorRequestDto request) {
        return connectorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        connectorService.delete(id);
    }

    @PostMapping("/{id}/publish")
    public ConnectorResponseDto publish(@PathVariable String id) {
        return connectorService.publish(id);
    }

    @GetMapping("/{id}/export")
    public ConnectorExportDto export(@PathVariable String id) {
        return connectorService.export(id);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public ConnectorResponseDto importConnector(@Valid @RequestBody ConnectorImportRequestDto request) {
        return connectorService.importConnector(request);
    }
}
