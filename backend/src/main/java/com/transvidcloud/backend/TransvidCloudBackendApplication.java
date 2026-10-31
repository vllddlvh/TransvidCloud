package com.transvidcloud.backend;

import com.transvidcloud.backend.common.config.TvcProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(TvcProperties.class)
public class TransvidCloudBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransvidCloudBackendApplication.class, args);
    }
}
