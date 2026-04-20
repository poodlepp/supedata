package com.dex.data.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一缓存服务：优先读写 Redis，失败时回退到进程内缓存。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, LocalCacheEntry> localCache = new ConcurrentHashMap<>();

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            localCache.put(key, new LocalCacheEntry(json, System.currentTimeMillis() + ttl.toMillis()));
            redisTemplate.opsForValue().set(key, json, ttl);
        } catch (Exception e) {
            log.warn("Failed to write cache key {}, keep local fallback only", key, e);
            try {
                String json = objectMapper.writeValueAsString(value);
                localCache.put(key, new LocalCacheEntry(json, System.currentTimeMillis() + ttl.toMillis()));
            } catch (Exception jsonError) {
                log.warn("Failed to serialize local fallback cache for key {}", key, jsonError);
            }
        }
    }

    public <T> Optional<T> get(String key, Class<T> valueType) {
        try {
            String redisValue = redisTemplate.opsForValue().get(key);
            if (redisValue != null && !redisValue.isBlank()) {
                return Optional.of(objectMapper.readValue(redisValue, valueType));
            }
        } catch (Exception e) {
            log.debug("Failed to read cache key {} from Redis, fallback to local cache", key, e);
        }

        LocalCacheEntry localEntry = localCache.get(key);
        if (localEntry == null) {
            return Optional.empty();
        }
        if (localEntry.expiresAt < System.currentTimeMillis()) {
            localCache.remove(key);
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(localEntry.json, valueType));
        } catch (Exception e) {
            log.warn("Failed to deserialize local cache key {}", key, e);
            localCache.remove(key);
            return Optional.empty();
        }
    }

    public void evict(String key) {
        localCache.remove(key);
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.debug("Failed to evict Redis cache key {}", key, e);
        }
    }

    private record LocalCacheEntry(String json, long expiresAt) {}
}
