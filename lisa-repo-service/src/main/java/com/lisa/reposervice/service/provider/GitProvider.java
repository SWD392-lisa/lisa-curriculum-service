package com.lisa.reposervice.service.provider;

import com.lisa.reposervice.dto.CreateRepoRequest;
import com.lisa.reposervice.dto.RepoResult;

public interface GitProvider {
    RepoResult createRepo(CreateRepoRequest request);
    boolean repoExists(String repoName);
    String platformName();
}
