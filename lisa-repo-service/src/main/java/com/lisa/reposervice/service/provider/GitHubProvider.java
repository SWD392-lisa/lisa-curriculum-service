package com.lisa.reposervice.service.provider;

import com.lisa.reposervice.config.GitHubConfig;
import com.lisa.reposervice.dto.CreateRepoRequest;
import com.lisa.reposervice.dto.RepoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubProvider implements GitProvider {

    private final GitHubConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String platformName() { return "GITHUB"; }

    @Override
    public boolean repoExists(String repoName) {
        try {
            String url = config.getApiUrl() + "/repos/" + config.getOrg() + "/" + repoName;
            restTemplate.exchange(url, HttpMethod.GET, buildEntity(null), Map.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("[GitHub] repoExists check failed for {}: {}", repoName, e.getMessage());
            return false;
        }
    }

    @Override
    public RepoResult createRepo(CreateRepoRequest request) {
        try {
            // Thử tạo trong org trước, nếu lỗi thì tạo dưới user
            String url = config.getApiUrl() + "/orgs/" + config.getOrg() + "/repos";

            Map<String, Object> body = new HashMap<>();
            body.put("name", request.getName());
            body.put("description", request.getDescription());
            body.put("private", request.isPrivateRepo());
            body.put("auto_init", request.isInitReadme());
            body.put("has_issues", true);
            body.put("has_projects", true);
            body.put("has_wiki", false);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, buildEntity(body), Map.class);

            Map<?, ?> resp = response.getBody();
            String htmlUrl    = (String) resp.get("html_url");
            String cloneHttps = (String) resp.get("clone_url");
            String cloneSsh   = (String) resp.get("ssh_url");

            // Gán topics (API riêng)
            if (request.getTopics() != null && !request.getTopics().isEmpty()) {
                addTopics(request.getName(), request.getTopics());
            }

            log.info("[GitHub] Created: {}", htmlUrl);
            return RepoResult.builder()
                    .platform("GITHUB").repoName(request.getName())
                    .repoUrl(htmlUrl).cloneUrlHttps(cloneHttps).cloneUrlSsh(cloneSsh)
                    .success(true).build();

        } catch (HttpClientErrorException e) {
            // Nếu org không tồn tại (404/422), fallback sang /user/repos
            if (e.getStatusCode() == HttpStatus.NOT_FOUND
                    || e.getStatusCode() == HttpStatus.UNPROCESSABLE_ENTITY) {
                return createUnderUser(request);
            }
            String msg = "GitHub API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString();
            log.error("[GitHub] {}", msg);
            return RepoResult.builder().platform("GITHUB").repoName(request.getName())
                    .success(false).errorMessage(msg).build();
        } catch (Exception e) {
            log.error("[GitHub] {}", e.getMessage());
            return RepoResult.builder().platform("GITHUB").repoName(request.getName())
                    .success(false).errorMessage(e.getMessage()).build();
        }
    }

    // Fallback: tạo dưới personal account
    private RepoResult createUnderUser(CreateRepoRequest request) {
        try {
            String url = config.getApiUrl() + "/user/repos";
            Map<String, Object> body = new HashMap<>();
            body.put("name", request.getName());
            body.put("description", request.getDescription());
            body.put("private", request.isPrivateRepo());
            body.put("auto_init", request.isInitReadme());

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, buildEntity(body), Map.class);
            Map<?, ?> resp = response.getBody();

            log.info("[GitHub] Created under user: {}", resp.get("html_url"));
            return RepoResult.builder()
                    .platform("GITHUB").repoName(request.getName())
                    .repoUrl((String) resp.get("html_url"))
                    .cloneUrlHttps((String) resp.get("clone_url"))
                    .cloneUrlSsh((String) resp.get("ssh_url"))
                    .success(true).build();
        } catch (Exception e) {
            return RepoResult.builder().platform("GITHUB").repoName(request.getName())
                    .success(false).errorMessage(e.getMessage()).build();
        }
    }

    private void addTopics(String repoName, List<String> topics) {
        try {
            String url = config.getApiUrl() + "/repos/" + config.getOrg() + "/" + repoName + "/topics";
            HttpHeaders headers = buildHeaders();
            headers.set("Accept", "application/vnd.github.mercy-preview+json");
            restTemplate.exchange(url, HttpMethod.PUT,
                    new HttpEntity<>(Map.of("names", topics), headers), Map.class);
        } catch (Exception e) {
            log.warn("[GitHub] Could not set topics for {}: {}", repoName, e.getMessage());
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + config.getToken());
        h.set("Accept", "application/vnd.github+json");
        h.set("X-GitHub-Api-Version", "2022-11-28");
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private <T> HttpEntity<T> buildEntity(T body) {
        return new HttpEntity<>(body, buildHeaders());
    }
}
