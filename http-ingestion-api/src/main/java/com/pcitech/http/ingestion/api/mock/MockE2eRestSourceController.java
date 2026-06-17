package com.pcitech.http.ingestion.api.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@Profile("e2e")
@RequestMapping("/mock/e2e")
public class MockE2eRestSourceController {

    private static final List<Map<String, Object>> WINDOW_ITEMS = List.of(
            Map.of("id", 1, "name", "Alice", "updated_at", "2025-06-01T08:00:00Z"),
            Map.of("id", 2, "name", "Bob", "updated_at", "2025-06-01T10:00:00Z"),
            Map.of("id", 3, "name", "Carol", "updated_at", "2025-06-01T12:00:00Z")
    );

    private static final List<Map<String, Object>> MONOTONIC_ITEMS = List.of(
            Map.of("id", 1, "name", "Alice"),
            Map.of("id", 2, "name", "Bob"),
            Map.of("id", 3, "name", "Carol"),
            Map.of("id", 4, "name", "Dave"),
            Map.of("id", 5, "name", "Eve")
    );

    @GetMapping("/kafka-users")
    public List<Map<String, Object>> kafkaUsers() {
        return List.of(
                Map.of("id", 1, "name", "Alice"),
                Map.of("id", 2, "name", "Bob")
        );
    }

    @GetMapping("/pagination-items")
    public Map<String, Object> paginationItems() {
        return Map.of(
                "data", List.of(Map.of("id", 1, "name", "Alice")),
                "meta", Map.of("total", 1)
        );
    }

    @GetMapping("/cursor-items")
    public Map<String, Object> cursorItems(@RequestParam(required = false) String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return Map.of(
                    "data", List.of(
                            Map.of("id", 1, "name", "Alice"),
                            Map.of("id", 2, "name", "Bob")
                    ),
                    "meta", Map.of("nextCursor", "page2", "hasMore", true)
            );
        }
        if ("page2".equals(cursor)) {
            return Map.of(
                    "data", List.of(Map.of("id", 3, "name", "Carol")),
                    "meta", Map.of("nextCursor", "", "hasMore", false)
            );
        }
        return Map.of(
                "data", List.of(),
                "meta", Map.of("nextCursor", "", "hasMore", false)
        );
    }

    @GetMapping("/link-items")
    public ResponseEntity<Map<String, Object>> linkItems(@RequestParam(defaultValue = "1") int page) {
        if (page == 1) {
            String nextUrl = ServletUriComponentsBuilder.fromCurrentRequestUri()
                    .replaceQueryParam("page", 2)
                    .build()
                    .toUriString();
            return ResponseEntity.ok()
                    .header("Link", "<" + nextUrl + ">; rel=\"next\"")
                    .body(Map.of(
                            "data", List.of(
                                    Map.of("id", 1, "name", "Alice"),
                                    Map.of("id", 2, "name", "Bob")
                            )
                    ));
        }
        return ResponseEntity.ok()
                .body(Map.of("data", List.of(Map.of("id", 3, "name", "Carol"))));
    }

    @GetMapping("/monotonic-items")
    public Map<String, Object> monotonicItems(@RequestParam(name = "since_id", required = false) String sinceId) {
        List<Map<String, Object>> filtered;
        if (sinceId == null || sinceId.isBlank()) {
            filtered = MONOTONIC_ITEMS;
        } else {
            long since = Long.parseLong(sinceId);
            filtered = MONOTONIC_ITEMS.stream()
                    .filter(item -> ((Number) item.get("id")).longValue() > since)
                    .collect(Collectors.toList());
        }
        return Map.of("data", filtered);
    }

    @GetMapping("/window-items")
    public Map<String, Object> windowItems(
            @RequestParam(name = "startTime", required = false) String startTime,
            @RequestParam(name = "endTime", required = false) String endTime
    ) {
        if (startTime == null || startTime.isBlank() || endTime == null || endTime.isBlank()) {
            return Map.of(
                    "data", List.of(WINDOW_ITEMS.get(0), WINDOW_ITEMS.get(1))
            );
        }
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);
        List<Map<String, Object>> filtered = WINDOW_ITEMS.stream()
                .filter(item -> {
                    Instant updatedAt = Instant.parse((String) item.get("updated_at"));
                    return !updatedAt.isBefore(start) && updatedAt.isBefore(end);
                })
                .collect(Collectors.toList());
        return Map.of("data", filtered);
    }
}
