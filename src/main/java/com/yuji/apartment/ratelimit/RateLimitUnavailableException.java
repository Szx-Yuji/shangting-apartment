package com.yuji.apartment.ratelimit;

public class RateLimitUnavailableException
        extends RuntimeException {

    public RateLimitUnavailableException() {
        super("限流服务暂时不可用");
    }

    public RateLimitUnavailableException(Throwable cause) {
        super("限流服务暂时不可用", cause);
    }
}