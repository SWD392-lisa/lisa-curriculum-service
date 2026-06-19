package com.lisa.curriculum.security;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "lms.security")
@Getter
@Setter
public class JwtProperties {
    private Jwt jwt = new Jwt();
    private Map<String, String> roleIdMap = new HashMap<>();

    @Getter
    @Setter
    public static class Jwt {
        private String secret;
        private String issuer;
        private String audience;
    }
}
