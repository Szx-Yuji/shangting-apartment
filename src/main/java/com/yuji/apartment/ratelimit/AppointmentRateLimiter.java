package com.yuji.apartment.ratelimit;

import com.yuji.apartment.config.AppointmentRateLimitProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class AppointmentRateLimiter {

    private final RedisFixedWindowRateLimiter limiter;
    private final AppointmentRateLimitProperties properties;

    public AppointmentRateLimiter(
            RedisFixedWindowRateLimiter limiter,
            AppointmentRateLimitProperties properties
    ) {
        this.limiter = limiter;
        this.properties = properties;
    }

    public boolean tryAcquire(String clientIp) {
        String key =
                "rate:appointment:ip:" + clientIp;

        try {
            return limiter.tryAcquire(
                    key,
                    properties.maxRequests(),
                    properties.windowSeconds()
            );
        } catch (RateLimitUnavailableException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "预约服务暂时不可用，请稍后重试"
            );
        }
    }
}