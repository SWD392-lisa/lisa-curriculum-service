package com.lisa.reposervice.service;

import com.lisa.reposervice.config.LisaServicesConfig;
import com.lisa.reposervice.dto.*;
import com.lisa.reposervice.service.provider.GitHubProvider;
import com.lisa.reposervice.service.provider.GitLabProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepoProvisionServiceTest {

    @Mock GitHubProvider gitHubProvider;
    @Mock GitLabProvider gitLabProvider;
    @Mock LisaServicesConfig lisaServicesConfig;
    @InjectMocks RepoProvisionService service;

    private LisaServicesConfig.ServiceDefinition lms;
    private LisaServicesConfig.ServiceDefinition realtime;

    @BeforeEach
    void setUp() {
        lms = new LisaServicesConfig.ServiceDefinition();
        lms.setName("lisa-lms-service"); lms.setDescription("LMS");
        lms.setTech("java-spring"); lms.setTopics(List.of("java"));

        realtime = new LisaServicesConfig.ServiceDefinition();
        realtime.setName("lisa-realtime-service"); realtime.setDescription("Realtime");
        realtime.setTech("nodejs"); realtime.setTopics(List.of("nodejs"));

        when(lisaServicesConfig.getServices()).thenReturn(List.of(lms, realtime));
    }

    @Test
    void provisionAll_allSuccess_returns4Results() {
        when(gitHubProvider.repoExists(anyString())).thenReturn(false);
        when(gitLabProvider.repoExists(anyString())).thenReturn(false);
        when(gitHubProvider.createRepo(any())).thenReturn(ok("GITHUB", "x"));
        when(gitLabProvider.createRepo(any())).thenReturn(ok("GITLAB", "x"));

        ProvisionResult result = service.provisionAllLisaServices(true, true);

        assertThat(result.getTotalRequested()).isEqualTo(4);   // 2 services × 2 platforms
        assertThat(result.getTotalSuccess()).isEqualTo(4);
        assertThat(result.getTotalFailed()).isEqualTo(0);
    }

    @Test
    void provisionAll_repoAlreadyExists_skipsAndCountsAsFailed() {
        when(gitHubProvider.repoExists("lisa-lms-service")).thenReturn(true);  // already exists
        when(gitHubProvider.repoExists("lisa-realtime-service")).thenReturn(false);
        when(gitLabProvider.repoExists(anyString())).thenReturn(false);
        when(gitHubProvider.createRepo(any())).thenReturn(ok("GITHUB", "x"));
        when(gitLabProvider.createRepo(any())).thenReturn(ok("GITLAB", "x"));

        ProvisionResult result = service.provisionAllLisaServices(true, true);

        assertThat(result.getTotalFailed()).isEqualTo(1);
        verify(gitHubProvider, times(1)).createRepo(any()); // chỉ tạo 1, skip lms
    }

    @Test
    void provisionSingle_knownService_success() {
        when(gitHubProvider.repoExists("lisa-lms-service")).thenReturn(false);
        when(gitLabProvider.repoExists("lisa-lms-service")).thenReturn(false);
        when(gitHubProvider.createRepo(any())).thenReturn(ok("GITHUB", "lisa-lms-service"));
        when(gitLabProvider.createRepo(any())).thenReturn(ok("GITLAB", "lisa-lms-service"));

        ProvisionResult result = service.provisionSingleService("lisa-lms-service", true, true);

        assertThat(result.getTotalSuccess()).isEqualTo(2);
        assertThat(result.getResults()).extracting(RepoResult::getPlatform)
                .containsExactlyInAnyOrder("GITHUB", "GITLAB");
    }

    @Test
    void provisionSingle_unknownService_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> service.provisionSingleService("unknown-svc", true, true));
    }

    @Test
    void createSingleRepo_platformBoth_callsBothProviders() {
        CreateRepoRequest req = CreateRepoRequest.builder()
                .name("custom-repo").platform("BOTH").build();
        when(gitHubProvider.repoExists("custom-repo")).thenReturn(false);
        when(gitLabProvider.repoExists("custom-repo")).thenReturn(false);
        when(gitHubProvider.createRepo(any())).thenReturn(ok("GITHUB", "custom-repo"));
        when(gitLabProvider.createRepo(any())).thenReturn(ok("GITLAB", "custom-repo"));

        List<RepoResult> results = service.createSingleRepo(req);

        assertThat(results).hasSize(2);
        verify(gitHubProvider).createRepo(any());
        verify(gitLabProvider).createRepo(any());
    }

    @Test
    void createSingleRepo_platformGithubOnly_neverCallsGitLab() {
        CreateRepoRequest req = CreateRepoRequest.builder()
                .name("gh-only").platform("GITHUB").build();
        when(gitHubProvider.repoExists("gh-only")).thenReturn(false);
        when(gitHubProvider.createRepo(any())).thenReturn(ok("GITHUB", "gh-only"));

        List<RepoResult> results = service.createSingleRepo(req);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getPlatform()).isEqualTo("GITHUB");
        verifyNoInteractions(gitLabProvider);
    }

    @Test
    void createSingleRepo_apiReturnsFailure_stillReturnsResult() {
        CreateRepoRequest req = CreateRepoRequest.builder()
                .name("fail-repo").platform("BOTH").build();
        when(gitHubProvider.repoExists("fail-repo")).thenReturn(false);
        when(gitLabProvider.repoExists("fail-repo")).thenReturn(false);
        when(gitHubProvider.createRepo(any())).thenReturn(
                RepoResult.builder().platform("GITHUB").repoName("fail-repo")
                        .success(false).errorMessage("Token invalid").build());
        when(gitLabProvider.createRepo(any())).thenReturn(ok("GITLAB", "fail-repo"));

        List<RepoResult> results = service.createSingleRepo(req);

        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(r -> !r.isSuccess()).count()).isEqualTo(1);
    }

    // ---- helper ----
    private RepoResult ok(String platform, String name) {
        return RepoResult.builder().platform(platform).repoName(name)
                .repoUrl("https://example.com/" + name)
                .cloneUrlHttps("https://example.com/" + name + ".git")
                .success(true).build();
    }
}
