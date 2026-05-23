package com.lisa.reposervice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "git.github")
@Data
public class GitHubConfig {
    private String token;
    private String org;
    private String apiUrl = "https://api.github.com";
}
