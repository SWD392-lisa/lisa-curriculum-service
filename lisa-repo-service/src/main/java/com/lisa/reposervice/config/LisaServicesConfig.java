package com.lisa.reposervice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "lisa")
@Data
public class LisaServicesConfig {
    private List<ServiceDefinition> services;

    @Data
    public static class ServiceDefinition {
        private String name;
        private String description;
        private String tech;
        private List<String> topics;
    }
}
