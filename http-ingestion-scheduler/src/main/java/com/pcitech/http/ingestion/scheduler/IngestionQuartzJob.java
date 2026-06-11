package com.pcitech.http.ingestion.scheduler;

import com.pcitech.http.ingestion.core.repository.ConnectorScheduleRepository;
import com.pcitech.http.ingestion.core.service.SyncService;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.beans.factory.annotation.Autowired;

public class IngestionQuartzJob implements Job {

    @Autowired
    private SyncService syncService;

    @Autowired
    private ConnectorScheduleRepository scheduleRepository;

    @Override
    public void execute(JobExecutionContext context) {
        String connectorId = context.getMergedJobDataMap().getString("connectorId");
        if (!scheduleRepository.isEnabled(connectorId)) {
            return;
        }
        syncService.triggerAsync(connectorId, SyncService.SyncOptions.incremental());
    }
}
