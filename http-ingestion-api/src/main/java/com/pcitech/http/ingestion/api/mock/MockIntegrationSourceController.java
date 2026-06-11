package com.pcitech.http.ingestion.api.mock;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/mock")
public class MockIntegrationSourceController {

    private final MockIntegrationSourceStore store;

    public MockIntegrationSourceController(MockIntegrationSourceStore store) {
        this.store = store;
    }

    @PostMapping("/dahua/gretrieval/vehicle/query")
    public Map<String, Object> dahuaVehicleQuery(@RequestBody JsonNode body) {
        JsonNode condition = body.path("condition");
        return store.queryDahuaVehicles(
                body.path("page").asInt(1),
                body.path("pageSize").asInt(100),
                text(condition, "startTime"),
                text(condition, "endTime")
        );
    }

    @PostMapping("/dahua/gretrieval/vehicle/count")
    public Map<String, Object> dahuaVehicleCount(@RequestBody JsonNode body) {
        JsonNode condition = body.path("condition");
        return store.countDahuaVehicles(
                text(condition, "startTime"),
                text(condition, "endTime")
        );
    }

    @PostMapping("/dahua/jg/trafficVehicle/illegal/queryList")
    public Map<String, Object> dahuaMotorIllegalQueryList(
            @RequestParam(value = "page", defaultValue = "1") Integer page,
            @RequestParam(value = "pageSize", defaultValue = "20") Integer pageSize,
            @RequestBody JsonNode body
    ) {
        return store.queryDahuaMotorIllegal(
                page,
                pageSize,
                text(body, "startTimeStrUtc"),
                text(body, "endTimeStrUtc"),
                text(body, "startTimeStr"),
                text(body, "endTimeStr")
        );
    }

    @PostMapping("/dahua/jg/trafficVehicle/illegal/count")
    public Map<String, Object> dahuaMotorIllegalCount(@RequestBody JsonNode body) {
        return store.countDahuaMotorIllegal(
                text(body, "startTimeStrUtc"),
                text(body, "endTimeStrUtc"),
                text(body, "startTimeStr"),
                text(body, "endTimeStr")
        );
    }

    @PostMapping("/meiya/api/res/trafficPoliceAlert")
    public Map<String, Object> meiyaTrafficPoliceAlert(@RequestBody JsonNode body) {
        JsonNode page = body.path("page");
        long skip = page.path("skip").asLong(0);
        long limit = page.path("limit").asLong(100);
        List<String> evccRange = readStringList(body.path("params").path("evcc"));
        return store.queryMeiyaTrafficPolice(skip, limit, evccRange);
    }

    @PostMapping("/meiya/api/res/dispatch110Flow")
    public Map<String, Object> meiyaDispatch110Flow(@RequestBody JsonNode body) {
        JsonNode page = body.path("page");
        long skip = page.path("skip").asLong(0);
        long limit = page.path("limit").asLong(100);
        List<String> gxsjRange = readStringList(body.path("params").path("gxsj"));
        return store.queryMeiyaDispatch110(skip, limit, gxsjRange);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }
}
