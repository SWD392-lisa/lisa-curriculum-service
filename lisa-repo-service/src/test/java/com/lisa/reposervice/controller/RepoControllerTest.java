package com.lisa.reposervice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lisa.reposervice.config.LisaServicesConfig;
import com.lisa.reposervice.dto.*;
import com.lisa.reposervice.service.RepoProvisionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RepoController.class)
class RepoControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @MockBean  RepoProvisionService provisionService;
    @MockBean  LisaServicesConfig lisaServicesConfig;

    @Test
    void provisionAll_returns200WithStats() throws Exception {
        ProvisionResult result = ProvisionResult.builder()
                .totalRequested(12).totalSuccess(12).totalFailed(0)
                .results(List.of(
                    RepoResult.builder().platform("GITHUB").repoName("lisa-lms-service")
                            .repoUrl("https://github.com/org/lisa-lms-service").success(true).build()
                )).build();
        when(provisionService.provisionAllLisaServices(true, true)).thenReturn(result);

        mockMvc.perform(post("/api/repos/provision/all")
                        .param("privateRepo", "true").param("initReadme", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRequested").value(12))
                .andExpect(jsonPath("$.totalSuccess").value(12))
                .andExpect(jsonPath("$.results[0].platform").value("GITHUB"));
    }

    @Test
    void provisionOne_returns200() throws Exception {
        ProvisionResult result = ProvisionResult.builder()
                .totalRequested(2).totalSuccess(2).totalFailed(0)
                .results(List.of(
                    RepoResult.builder().platform("GITHUB").repoName("lisa-lms-service").success(true).build(),
                    RepoResult.builder().platform("GITLAB").repoName("lisa-lms-service").success(true).build()
                )).build();
        when(provisionService.provisionSingleService("lisa-lms-service", true, true)).thenReturn(result);

        mockMvc.perform(post("/api/repos/provision/lisa-lms-service")
                        .param("privateRepo", "true").param("initReadme", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSuccess").value(2));
    }

    @Test
    void createCustom_returns200WithBothPlatforms() throws Exception {
        CreateRepoRequest req = CreateRepoRequest.builder()
                .name("custom").description("desc")
                .privateRepo(true).platform("BOTH").initReadme(true).build();
        when(provisionService.createSingleRepo(any())).thenReturn(List.of(
                RepoResult.builder().platform("GITHUB").repoName("custom")
                        .repoUrl("https://github.com/org/custom").success(true).build(),
                RepoResult.builder().platform("GITLAB").repoName("custom")
                        .repoUrl("https://gitlab.com/group/custom").success(true).build()
        ));

        mockMvc.perform(post("/api/repos/custom")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].platform").value("GITHUB"))
                .andExpect(jsonPath("$[1].platform").value("GITLAB"))
                .andExpect(jsonPath("$[0].success").value(true));
    }

    @Test
    void listServices_returns200() throws Exception {
        LisaServicesConfig.ServiceDefinition svc = new LisaServicesConfig.ServiceDefinition();
        svc.setName("lisa-lms-service"); svc.setTech("java-spring");
        svc.setTopics(List.of("java", "spring-boot"));
        when(lisaServicesConfig.getServices()).thenReturn(List.of(svc));

        mockMvc.perform(get("/api/repos/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("lisa-lms-service"))
                .andExpect(jsonPath("$[0].tech").value("java-spring"));
    }

    @Test
    void provisionOne_unknownService_returns500() throws Exception {
        when(provisionService.provisionSingleService(eq("unknown"), anyBoolean(), anyBoolean()))
                .thenThrow(new IllegalArgumentException("Service not found: unknown"));

        mockMvc.perform(post("/api/repos/provision/unknown"))
                .andExpect(status().is5xxServerError());
    }
}
