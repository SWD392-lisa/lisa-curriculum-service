package com.lisa.curriculum.service;

import com.lisa.curriculum.exception.AiProviderUnavailableException;
import com.lisa.curriculum.exception.RateLimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class SupportRateLimiter {
    private final StringRedisTemplate redis;

    public void check(String userId) {
        long window = Instant.now().getEpochSecond() / 600;
        String key = "ai:support:" + userId + ":" + window;
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) redis.expire(key, Duration.ofMinutes(11));
            if (count != null && count > 20) throw new RateLimitExceededException("Bạn đã đạt giới hạn 20 câu hỏi trong 10 phút.");
        } catch (RateLimitExceededException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiProviderUnavailableException("Support rate limiter is unavailable", ex);
        }
    }
}
