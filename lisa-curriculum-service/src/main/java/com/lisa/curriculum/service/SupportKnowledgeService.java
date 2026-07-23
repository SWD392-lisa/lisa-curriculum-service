package com.lisa.curriculum.service;

import com.lisa.curriculum.exception.AiProviderUnavailableException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;

@Service
public class SupportKnowledgeService {
    private final String knowledge;

    public SupportKnowledgeService() {
        try {
            knowledge = new ClassPathResource("support/lucy-support-knowledge.md")
                    .getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new AiProviderUnavailableException("Support knowledge base could not be loaded", ex);
        }
    }

    public String content() { return knowledge; }
}
