package com.pcitech.http.ingestion.api.web;

import com.pcitech.http.ingestion.core.dto.IngestionStatsDto;
import com.pcitech.http.ingestion.core.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public IngestionStatsDto get(@RequestParam(defaultValue = "50") int recentLimit) {
        return statsService.getStats(Math.min(Math.max(recentLimit, 1), 200));
    }
}
