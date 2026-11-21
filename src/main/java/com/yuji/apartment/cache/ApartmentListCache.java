package com.yuji.apartment.cache;

import com.yuji.apartment.dto.ApartmentPageResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class ApartmentListCache {
    private static final Logger log =
            LoggerFactory.getLogger(ApartmentListCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ApartmentListCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public ApartmentPageResponse get(String key) {
        try {
            String json = redisTemplate.opsForValue().get(key);

            if (json == null) {
                return null;
            }

            ApartmentPageResponse page = objectMapper.readValue(
                    json, ApartmentPageResponse.class
            );

            if (page == null || page.records() == null) {
                return null;
            }

            return page;
        } catch (DataAccessException | JacksonException exception) {
            log.warn("公寓列表缓存读取失败，按未命中处理");
            return null;
        }
    }

    public void put(String key, ApartmentPageResponse page) {
        try {
            String json = objectMapper.writeValueAsString(page);

            int ttlSeconds = ThreadLocalRandom.current()
                    .nextInt(240, 301);

            redisTemplate.opsForValue().set(
                    key,
                    json,
                    Duration.ofSeconds(ttlSeconds)
            );
        } catch (DataAccessException | JacksonException exception) {
            log.warn("公寓列表缓存写入失败，跳过本次缓存");
        }
    }

    private void evictAll() {
        ScanOptions options = ScanOptions.scanOptions()
                .match("public:apartments:v1:*")
                .count(100)
                .build();

        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                redisTemplate.delete(cursor.next());
            }
        } catch (DataAccessException exception) {
            log.warn("公寓列表缓存清理失败，等待缓存过期");
        }
    }

    public void evictAllAfterCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictAll();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictAll();
                    }
                }
        );
    }
}