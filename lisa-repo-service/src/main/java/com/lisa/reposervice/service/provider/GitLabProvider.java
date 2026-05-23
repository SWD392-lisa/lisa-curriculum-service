package com.lisa.reposervice.service.provider;

import com.lisa.reposervice.config.GitLabConfig;
import com.lisa.reposervice.dto.CreateRepoRequest;
import com.lisa.reposervice.dto.RepoResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GitLabProvider implements GitProvider {

    private final GitLabConfig config;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String platformName() { return "GITLAB"; }

    @Override
    public boolean repoExists(String repoName) {
        try {
            String path = config.getNamespaceId() + "%2F" + repoName;
            String url = config.getApiUrl() + "/projects/" + path;
            restTemplate.exchange(url, HttpMethod.GET, buildEntity(null), Map.class);
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception e) {
            log.warn("[GitLab] repoExists check failed for {}: {}", repoName, e.getMessage());
            return false;
        }
    }

    @Override
    public RepoResult createRepo(CreateRepoRequest request) {
        try {
            String url = config.getApiUrl() + "/projects";

            Map<String, Object> body = new HashMap<>();
            body.put("name", request.getName());
            body.put("path", request.getName());
            body.put("description", request.getDescription());
            body.put("namespace_id", config.getNamespaceId());
            body.put("visibility", request.isPrivateRepo() ? "private" : "public");
            body.put("initialize_with_readme", request.isInitReadme());
            body.put("issues_access_level", "enabled");
            body.put("merge_requests_access_level", "enabled");
            body.put("wiki_access_level", "disabled");
            if (request.getTopics() != null && !request.getTopics().isEmpty()) {
                body.put("topics", request.getTopics());
            }

            ResponseEntity<Map> response = restTemplate.exchange(
                    url, HttpMethod.POST, buildEntity(body), Map.class);

            Map<?, ?> resp = response.getBody();
            String webUrl     = (String) resp.get("web_url");
            String cloneHttps = (String) resp.get("http_url_to_repo");
            String cloneSsh   = (String) resp.get("ssh_url_to_repo");

            log.info("[GitLab] Created: {}", webUrl);
            return RepoResult.builder()
                    .platform("GITLAB").repoName(request.getName())
                    .repoUrl(webUrl).cloneUrlHttps(cloneHttps).cloneUrlSsh(cloneSsh)
                    .success(true).build();

        } catch (HttpClientErrorException e) {
            String msg = "GitLab API error " + e.getStatusCode() + ": " + e.getResponseBodyAsString();
            log.error("[GitLab] {}", msg);
            return RepoResult.builder().platform("GITLAB").repoName(request.getName())
                    .success(false).errorMessage(msg).build();
        } catch (Exception e) {
            log.error("[GitLab] {}", e.getMessage());
            return RepoResult.builder().platform("GITLAB").repoName(request.getName())
                    .success(false).errorMessage(e.getMessage()).build();
        }
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("PRIVATE-TOKEN", config.getToken());
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private <T> HttpEntity<T> buildEntity(T body) {
        return new HttpEntity<>(body, buildHeaders());
    }
}
