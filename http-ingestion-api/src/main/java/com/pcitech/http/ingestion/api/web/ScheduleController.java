package com.pcitech.http.ingestion.api.web;

import com.pcitech.http.ingestion.core.dto.ConnectorScheduleDto;
import com.pcitech.http.ingestion.scheduler.ConnectorScheduleService;
import org.quartz.SchedulerException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/connectors/{id}/schedule")
public class ScheduleController {

    private final ConnectorScheduleService scheduleService;

    public ScheduleController(ConnectorScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @GetMapping
    public ConnectorScheduleDto get(@PathVariable String id) throws SchedulerException {
        return scheduleService.getSchedule(id);
    }

    @PostMapping("/pause")
    public ConnectorScheduleDto pause(@PathVariable String id) throws SchedulerException {
        scheduleService.pauseSchedule(id);
        return scheduleService.getSchedule(id);
    }

    @PostMapping("/resume")
    public ConnectorScheduleDto resume(@PathVariable String id) throws SchedulerException {
        scheduleService.resumeSchedule(id);
        return scheduleService.getSchedule(id);
    }
}
