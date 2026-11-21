package com.yuji.apartment.ratelimit;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RedisFixedWindowRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> script;

    public RedisFixedWindowRateLimiter(
            StringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;

        this.script = new DefaultRedisScript<>();
        this.script.setLocation(
                new ClassPathResource(
                        "redis/appointment-rate-limit.lua"
                )
        );
        this.script.setResultType(Long.class);

        if (this.script.getScriptAsString().isBlank()) {
            throw new IllegalStateException(
                    "限流脚本不能为空"
            );
        }
    }

    public boolean tryAcquire(
            String key,
            int maxRequests,
            int windowSeconds
    ) {
        if (key == null
                || key.isBlank()
                || maxRequests <= 0
                || windowSeconds <= 0) {
            throw new IllegalArgumentException(
                    "限流参数必须有效且次数和窗口必须大于0"
            );
        }

        Long result;

        try {
            result = redisTemplate.execute(
                    script,
                    List.of(key),
                    String.valueOf(maxRequests),
                    String.valueOf(windowSeconds)
            );
        } catch (DataAccessException exception) {
            throw new RateLimitUnavailableException(exception);
        }

        if (result == null
                || (result != 0L && result != 1L)) {
            throw new RateLimitUnavailableException();
        }

        return result == 1L;
    }
}