package com.pcitech.http.ingestion.api.mock;

import com.pcitech.http.ingestion.core.dto.JiaduResultInfo;
import com.pcitech.http.ingestion.core.webhook.JiaduSignVerifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@Profile("e2e")
@RequestMapping("/mock/jiadu")
public class MockJiaduPushSimulator {

    private final MockJiaduPushStore store;
    private final RestTemplate restTemplate = new RestTemplate();

    public MockJiaduPushSimulator(MockJiaduPushStore store) {
        this.store = store;
    }

    @PostMapping("/push/{connectorId}")
    public Map<String, Object> push(
            @PathVariable String connectorId,
            @RequestParam(defaultValue = "10") int rounds,
            @RequestParam(defaultValue = "false") boolean sign,
            @RequestParam(defaultValue = "ivsp") String platFlag,
            @RequestParam(defaultValue = "sim") String idPrefix
    ) {
        String ingressUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/ingress/{connectorId}")
                .buildAndExpand(connectorId)
                .toUriString();

        List<Map<String, Object>> results = new ArrayList<>();
        int success = 0;
        for (int i = 0; i < rounds; i++) {
            Map<String, Object> event = store.nextEvent(idPrefix);
            JiaduResultInfo response = postEvent(ingressUrl, event, sign, platFlag);
            results.add(Map.of(
                    "eventId", event.get("EventID"),
                    "opCode", response.opCode(),
                    "opDesc", response.opDesc()
            ));
            if (response.opCode() == 0) {
                success++;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("ok", true);
        payload.put("connectorId", connectorId);
        payload.put("sent", rounds);
        payload.put("success", success);
        payload.put("results", results);
        return payload;
    }

    public JiaduResultInfo postEvent(
            String ingressUrl,
            Map<String, Object> event,
            boolean sign,
            String platFlag
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x_request_id", String.valueOf(event.get("EventID")));
        if (sign) {
            headers.set("sign", JiaduSignVerifier.compute(platFlag, String.valueOf(event.get("EventID"))));
        }
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(event, headers);
        return restTemplate.postForObject(ingressUrl, entity, JiaduResultInfo.class);
    }
}
