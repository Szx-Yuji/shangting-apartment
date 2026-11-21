package com.yuji.apartment.ratelimit;

import com.yuji.apartment.config.AdminLoginRateLimitProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AdminLoginRateLimiter {

    private final RedisFixedWindowRateLimiter limiter;
    private final AdminLoginRateLimitProperties properties;

    public AdminLoginRateLimiter(
            RedisFixedWindowRateLimiter limiter,
            AdminLoginRateLimitProperties properties
    ) {
        this.limiter = limiter;
        this.properties = properties;
    }

    public boolean tryAcquire(String clientIp) {
        String key = "rate:admin-login:ip:" + clientIp;

        try {
            return limiter.tryAcquire(
                    key,
                    properties.maxRequests(),
                    properties.windowSeconds()
            );
        } catch (RateLimitUnavailableException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "登录服务暂时不可用，请稍后重试"
            );
        }
    }
}