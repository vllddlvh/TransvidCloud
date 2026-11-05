package com.transvidcloud.backend;

import com.transvidcloud.backend.common.config.TvcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(TvcProperties.class)
public class TransvidCloudBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransvidCloudBackendApplication.class, args);
    }
}
