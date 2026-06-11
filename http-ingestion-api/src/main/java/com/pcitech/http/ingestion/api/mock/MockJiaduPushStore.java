package com.pcitech.http.ingestion.api.mock;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class MockJiaduPushStore {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AtomicInteger sequence = new AtomicInteger(1);

    public Map<String, Object> nextEvent(String idPrefix) {
        int index = sequence.getAndIncrement();
        String now = LocalDateTime.now().format(TIME);
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("EventID", idPrefix + "-" + String.format("%05d", index));
        event.put("EventType", 3001);
        event.put("EventName", "模拟事件-" + index);
        event.put("SendTime", now);
        event.put("CameraID", "CAM-" + (index % 100));
        event.put("ImgUrl", "http://example/alarm-" + index + ".jpg");
        event.put("EventTime", now);
        event.put("Confidence", 0.95);
        event.put("TaskID", "TASK-" + index);
        event.put("EventGroup", 1);
        event.put("Census", 1);
        event.put("InterDay", 0);
        event.put("EnterNumber", 0);
        event.put("OutNumber", 0);
        return event;
    }

    public Map<String, Object> eventWithImageUrlField(String eventId) {
        Map<String, Object> event = new LinkedHashMap<>(nextEvent("imgurl"));
        event.put("EventID", eventId);
        event.remove("ImgUrl");
        event.put("ImageUrl", "http://example/image-url-" + eventId + ".jpg");
        return event;
    }
}
