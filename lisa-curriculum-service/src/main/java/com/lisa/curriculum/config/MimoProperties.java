package com.lisa.curriculum.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "mimo")
@Getter
@Setter
public class MimoProperties {
    private String baseUrl;
    private String apiKey;
    private String model = "MiMo-V2-Flash";
    private long timeoutMs = 20000;
    private int maxTokens = 500;
}
