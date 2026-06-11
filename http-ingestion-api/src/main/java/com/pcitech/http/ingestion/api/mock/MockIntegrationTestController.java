package com.pcitech.http.ingestion.api.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@Profile("e2e")
@RequestMapping("/mock/_test")
public class MockIntegrationTestController {

    private final MockIntegrationSourceStore store;

    public MockIntegrationTestController(MockIntegrationSourceStore store) {
        this.store = store;
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        store.reset();
        return Map.of("ok", true);
    }

    @PostMapping("/dahua/vehicles")
    public Map<String, Object> appendDahua(@RequestBody Map<String, Object> record) {
        store.addDahuaVehicle(record);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("total", store.dahuaVehicleCount());
        return response;
    }

    @PostMapping("/dahua/motor-illegal")
    public Map<String, Object> appendDahuaMotorIllegal(@RequestBody Map<String, Object> record) {
        store.addDahuaMotorIllegal(record);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("total", store.dahuaMotorIllegalCount());
        return response;
    }

    @PostMapping("/meiya/traffic-police")
    public Map<String, Object> appendMeiyaTrafficPolice(@RequestBody Map<String, Object> record) {
        store.addMeiyaTrafficPolice(record);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("total", store.meiyaTrafficPoliceCount());
        return response;
    }

    @PostMapping("/meiya/dispatch110")
    public Map<String, Object> appendMeiyaDispatch110(@RequestBody Map<String, Object> record) {
        store.addMeiyaDispatch110(record);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("ok", true);
        response.put("total", store.meiyaDispatch110Count());
        return response;
    }
}
