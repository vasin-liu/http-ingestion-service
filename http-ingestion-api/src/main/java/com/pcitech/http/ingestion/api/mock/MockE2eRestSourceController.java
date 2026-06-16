package com.pcitech.http.ingestion.api.mock;

import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.Map;

@RestController
@Profile("e2e")
@RequestMapping("/mock/e2e")
public class MockE2eRestSourceController {

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
}
