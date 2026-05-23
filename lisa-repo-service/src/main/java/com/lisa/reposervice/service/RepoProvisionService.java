package com.lisa.reposervice.service;

import com.lisa.reposervice.config.LisaServicesConfig;
import com.lisa.reposervice.dto.*;
import com.lisa.reposervice.service.provider.GitHubProvider;
import com.lisa.reposervice.service.provider.GitLabProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RepoProvisionService {

    private final GitHubProvider gitHubProvider;
    private final GitLabProvider gitLabProvider;
    private final LisaServicesConfig lisaServicesConfig;

    /** Tạo repo đơn lẻ theo platform chỉ định */
    public List<RepoResult> createSingleRepo(CreateRepoRequest request) {
        List<RepoResult> results = new ArrayList<>();
        String platform = request.getPlatform() == null ? "BOTH" : request.getPlatform().toUpperCase();

        if ("GITHUB".equals(platform) || "BOTH".equals(platform)) {
            results.add(gitHubProvider.repoExists(request.getName())
                    ? skipped("GITHUB", request.getName(), "Already exists on GitHub")
                    : gitHubProvider.createRepo(request));
        }
        if ("GITLAB".equals(platform) || "BOTH".equals(platform)) {
            results.add(gitLabProvider.repoExists(request.getName())
                    ? skipped("GITLAB", request.getName(), "Already exists on GitLab")
                    : gitLabProvider.createRepo(request));
        }
        return results;
    }

    /** Provision TẤT CẢ services khai báo trong application.yml → GitHub + GitLab */
    public ProvisionResult provisionAllLisaServices(boolean privateRepo, boolean initReadme) {
        List<RepoResult> all = new ArrayList<>();
        log.info("Provisioning {} LISA services on GitHub + GitLab...",
                lisaServicesConfig.getServices().size());

        for (LisaServicesConfig.ServiceDefinition svc : lisaServicesConfig.getServices()) {
            log.info("→ {}", svc.getName());
            CreateRepoRequest req = CreateRepoRequest.builder()
                    .name(svc.getName()).description(svc.getDescription())
                    .privateRepo(privateRepo).initReadme(initReadme)
                    .topics(svc.getTopics()).platform("BOTH").build();
            all.addAll(createSingleRepo(req));
            sleep(400); // avoid rate limit
        }
        return toResult(all);
    }

    /** Provision 1 service cụ thể theo tên */
    public ProvisionResult provisionSingleService(String serviceName,
                                                   boolean privateRepo, boolean initReadme) {
        LisaServicesConfig.ServiceDefinition svc = lisaServicesConfig.getServices().stream()
                .filter(s -> s.getName().equalsIgnoreCase(serviceName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceName));

        CreateRepoRequest req = CreateRepoRequest.builder()
                .name(svc.getName()).description(svc.getDescription())
                .privateRepo(privateRepo).initReadme(initReadme)
                .topics(svc.getTopics()).platform("BOTH").build();

        return toResult(createSingleRepo(req));
    }

    // ---- helpers ----
    private RepoResult skipped(String platform, String name, String reason) {
        return RepoResult.builder().platform(platform).repoName(name)
                .success(false).errorMessage(reason).build();
    }

    private ProvisionResult toResult(List<RepoResult> results) {
        long ok   = results.stream().filter(RepoResult::isSuccess).count();
        long fail = results.size() - ok;
        return ProvisionResult.builder()
                .totalRequested(results.size()).totalSuccess((int) ok)
                .totalFailed((int) fail).results(results).build();
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
