package com.pcitech.http.ingestion.api.ingress;

import com.fasterxml.jackson.databind.JsonNode;
import com.pcitech.http.ingestion.core.dto.JiaduResultInfo;
import com.pcitech.http.ingestion.core.service.PushIngressService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JiaduIngressController {

    private final PushIngressService pushIngressService;

    public JiaduIngressController(PushIngressService pushIngressService) {
        this.pushIngressService = pushIngressService;
    }

    @PostMapping("/ingress/{connectorId}")
    public JiaduResultInfo ingest(
            @PathVariable String connectorId,
            @RequestHeader(value = "x_request_id", required = false) String requestId,
            @RequestHeader(value = "sign", required = false) String sign,
            @RequestBody JsonNode body
    ) {
        return pushIngressService.ingest(connectorId, requestId, sign, body);
    }
}
