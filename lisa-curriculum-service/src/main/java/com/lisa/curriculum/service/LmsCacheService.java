package com.lisa.curriculum.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

@Service
public class LmsCacheService {

    private final CacheManager cacheManager;

    public LmsCacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void evictMentorDashboard(String mentorId) {
        evict("mentor_dashboard", mentorId);
    }

    public void clearAllMentorDashboards() {
        clear("mentor_dashboard");
    }

    public void evictLearnerProgress(String learnerId) {
        evict("learner_progress", learnerId);
    }

    public void evictRoomState(Object sessionId) {
        evict("room_state", sessionId);
    }

    public void evictRecordings(Object sessionId) {
        evict("recordings", sessionId);
    }

    private void evict(String cacheName, Object key) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null && key != null) {
            cache.evict(key);
        }
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }
}
