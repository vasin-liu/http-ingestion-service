package com.pcitech.http.ingestion;

import com.pcitech.http.ingestion.api.config.ApiAutoConfiguration;
import com.pcitech.http.ingestion.core.config.CoreAutoConfiguration;
import com.pcitech.http.ingestion.scheduler.SchedulerAutoConfiguration;
import com.pcitech.http.ingestion.sink.kafka.KafkaSinkAutoConfiguration;
import com.pcitech.http.ingestion.sink.pg.PgSinkAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
        CoreAutoConfiguration.class,
        ApiAutoConfiguration.class,
        PgSinkAutoConfiguration.class,
        KafkaSinkAutoConfiguration.class,
        SchedulerAutoConfiguration.class
})
public class HttpIngestionApplication {

    public static void main(String[] args) {
        SpringApplication.run(HttpIngestionApplication.class, args);
    }
}
