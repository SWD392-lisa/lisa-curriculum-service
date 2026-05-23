package com.lisa.reposervice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "git.gitlab")
@Data
public class GitLabConfig {
    private String token;
    private String namespaceId;
    private String apiUrl = "https://gitlab.com/api/v4";
}
