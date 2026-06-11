package com.pcitech.http.ingestion.scheduler;

import org.springframework.boot.autoconfigure.quartz.SchedulerFactoryBeanCustomizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;

@Configuration
public class QuartzSpringConfig implements SchedulerFactoryBeanCustomizer {

    private final AutowiringSpringBeanJobFactory jobFactory;

    public QuartzSpringConfig(AutowiringSpringBeanJobFactory jobFactory) {
        this.jobFactory = jobFactory;
    }

    @Override
    public void customize(SchedulerFactoryBean schedulerFactoryBean) {
        schedulerFactoryBean.setJobFactory(jobFactory);
    }
}
