package com.yuji.apartment.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.admin-login-rate-limit")
public record AdminLoginRateLimitProperties(
        @Positive int maxRequests,
        @Positive int windowSeconds
) {
}