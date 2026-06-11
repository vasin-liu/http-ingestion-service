package com.pcitech.http.ingestion.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConfigParser;
import com.pcitech.http.ingestion.core.config.runtime.RuntimeConnectorConfig;
import com.pcitech.http.ingestion.core.dto.ConnectorScheduleDto;
import com.pcitech.http.ingestion.core.event.ConnectorPublishedEvent;
import com.pcitech.http.ingestion.core.repository.ConnectorScheduleRepository;
import com.pcitech.http.ingestion.core.repository.ConnectorScheduleRow;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConnectorScheduleService {

    private final Scheduler scheduler;
    private final ConnectorScheduleRepository scheduleRepository;
    private final ObjectMapper objectMapper;

    public ConnectorScheduleService(
            Scheduler scheduler,
            ConnectorScheduleRepository scheduleRepository,
            ObjectMapper objectMapper
    ) {
        this.scheduler = scheduler;
        this.scheduleRepository = scheduleRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onPublished(ConnectorPublishedEvent event) throws Exception {
        RuntimeConnectorConfig config = RuntimeConfigParser.parse(
                objectMapper.readTree(event.configJson()),
                objectMapper
        );
        RuntimeConnectorConfig.ScheduleSettings schedule = config.schedule();
        String expressionForDb = storageExpression(schedule);
        scheduleRepository.upsert(
                event.connectorId(),
                schedule.type(),
                expressionForDb,
                schedule.enabled()
        );
        if (schedule.enabled()) {
            registerJob(event.connectorId(), schedule);
        } else {
            unregisterJob(event.connectorId());
        }
    }

    public ConnectorScheduleDto getSchedule(String connectorId) throws SchedulerException {
        boolean registered = scheduler.checkExists(jobKey(connectorId));
        return scheduleRepository.findByConnectorId(connectorId)
                .map(row -> toDto(connectorId, row, registered))
                .orElseGet(() -> new ConnectorScheduleDto(
                        connectorId, null, null, false, registered, false));
    }

    public void pauseSchedule(String connectorId) throws SchedulerException {
        JobKey key = jobKey(connectorId);
        if (!scheduler.checkExists(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not registered");
        }
        scheduler.pauseJob(key);
        scheduler.pauseTrigger(triggerKey(connectorId));
        scheduleRepository.setEnabled(connectorId, false);
    }

    public void resumeSchedule(String connectorId) throws SchedulerException {
        JobKey key = jobKey(connectorId);
        if (!scheduler.checkExists(key)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Schedule not registered");
        }
        scheduler.resumeTrigger(triggerKey(connectorId));
        scheduler.resumeJob(key);
        scheduleRepository.setEnabled(connectorId, true);
    }

    public void registerJob(String connectorId, RuntimeConnectorConfig.ScheduleSettings schedule) throws SchedulerException {
        JobKey jobKey = jobKey(connectorId);
        JobDetail jobDetail = JobBuilder.newJob(IngestionQuartzJob.class)
                .withIdentity(jobKey)
                .usingJobData("connectorId", connectorId)
                .build();
        Trigger trigger = buildTrigger(connectorId, schedule);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
        }
        scheduler.scheduleJob(jobDetail, trigger);
    }

    public void registerJob(String connectorId, String cronExpression) throws SchedulerException {
        registerJob(connectorId, new RuntimeConnectorConfig.ScheduleSettings(
                true, "cron", cronExpression, null));
    }

    public void unregisterJob(String connectorId) throws SchedulerException {
        scheduler.deleteJob(jobKey(connectorId));
    }

    private Trigger buildTrigger(String connectorId, RuntimeConnectorConfig.ScheduleSettings schedule) {
        if ("fixed_rate".equalsIgnoreCase(schedule.type())) {
            int seconds = resolveIntervalSeconds(schedule);
            return TriggerBuilder.newTrigger()
                    .withIdentity("trigger-" + connectorId, "ingestion")
                    .withSchedule(SimpleScheduleBuilder.simpleSchedule()
                            .withIntervalInSeconds(seconds)
                            .repeatForever())
                    .build();
        }
        return TriggerBuilder.newTrigger()
                .withIdentity("trigger-" + connectorId, "ingestion")
                .withSchedule(CronScheduleBuilder.cronSchedule(normalizeCron(schedule.expression())))
                .build();
    }

    private String storageExpression(RuntimeConnectorConfig.ScheduleSettings schedule) {
        if ("fixed_rate".equalsIgnoreCase(schedule.type())) {
            return String.valueOf(resolveIntervalSeconds(schedule));
        }
        return schedule.expression();
    }

    private int resolveIntervalSeconds(RuntimeConnectorConfig.ScheduleSettings schedule) {
        if (schedule.intervalSeconds() != null && schedule.intervalSeconds() > 0) {
            return schedule.intervalSeconds();
        }
        if (schedule.expression() != null && !schedule.expression().isBlank()) {
            try {
                int parsed = Integer.parseInt(schedule.expression().trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return 60;
    }

    private ConnectorScheduleDto toDto(
            String connectorId,
            ConnectorScheduleRow row,
            boolean registered
    ) {
        boolean paused = registered && !row.enabled();
        return new ConnectorScheduleDto(
                connectorId,
                row.scheduleType(),
                row.expression(),
                row.enabled(),
                registered,
                paused
        );
    }

    private JobKey jobKey(String connectorId) {
        return JobKey.jobKey("connector-" + connectorId, "ingestion");
    }

    private org.quartz.TriggerKey triggerKey(String connectorId) {
        return org.quartz.TriggerKey.triggerKey("trigger-" + connectorId, "ingestion");
    }

    private String normalizeCron(String expression) {
        String trimmed = expression == null ? "" : expression.trim();
        int parts = trimmed.split("\\s+").length;
        if (parts == 5) {
            return "0 " + trimmed;
        }
        return trimmed;
    }
}
