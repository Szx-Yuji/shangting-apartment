package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AdminLoginRequest;
import com.yuji.apartment.dto.UserAccountResponse;
import com.yuji.apartment.dto.UserRegisterRequest;
import com.yuji.apartment.ratelimit.RateLimitUnavailableException;
import com.yuji.apartment.ratelimit.RedisFixedWindowRateLimiter;
import com.yuji.apartment.service.UserAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class UserAuthController {
    private final UserAuthService auth;
    private final RedisFixedWindowRateLimiter limiter;

    public UserAuthController(UserAuthService auth, RedisFixedWindowRateLimiter limiter) {
        this.auth = auth;
        this.limiter = limiter;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserAccountResponse register(@Valid @RequestBody UserRegisterRequest body, HttpServletRequest request) {
        checkRate(request, "register", 5, 900);
        UserAccountResponse account = auth.register(body);
        signIn(request, account.id());
        return account;
    }

    @PostMapping("/login")
    public UserAccountResponse login(@Valid @RequestBody AdminLoginRequest body, HttpServletRequest request) {
        checkRate(request, "login", 10, 900);
        UserAccountResponse account = auth.authenticate(body.username(), body.password());
        signIn(request, account.id());
        return account;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }

    private void signIn(HttpServletRequest request, Long id) {
        HttpSession old = request.getSession(false);
        if (old != null) old.invalidate();
        HttpSession session = request.getSession(true);
        session.setMaxInactiveInterval(1800);
        session.setAttribute("userId", id);
    }

    private void checkRate(HttpServletRequest request, String action, int count, int seconds) {
        try {
            if (!limiter.tryAcquire("rate:user-" + action + ":ip:" + request.getRemoteAddr(), count, seconds)) {
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试");
            }
        } catch (RateLimitUnavailableException exception) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "登录服务暂时不可用，请稍后重试");
        }
    }
}
