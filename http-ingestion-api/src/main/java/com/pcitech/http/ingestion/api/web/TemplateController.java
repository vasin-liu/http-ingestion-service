package com.pcitech.http.ingestion.api.web;

import com.pcitech.http.ingestion.core.dto.ConnectorTemplateDto;
import com.pcitech.http.ingestion.core.service.ConnectorTemplateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final ConnectorTemplateService templateService;

    public TemplateController(ConnectorTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    public List<ConnectorTemplateDto> list() {
        return templateService.list();
    }

    @GetMapping("/{id}")
    public ConnectorTemplateDto get(@PathVariable String id) {
        return templateService.get(id);
    }
}
