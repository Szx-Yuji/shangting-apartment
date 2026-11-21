package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AdminAccountResponse;
import com.yuji.apartment.dto.AdminLoginRequest;
import com.yuji.apartment.service.AdminAuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import com.yuji.apartment.ratelimit.AdminLoginRateLimiter;
import org.springframework.web.bind.annotation.ResponseStatus;
import com.yuji.apartment.service.AdminSessionService;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;
    private final AdminSessionService adminSessionService;
    private final AdminLoginRateLimiter loginRateLimiter;

    public AdminAuthController(
            AdminAuthService adminAuthService,
            AdminSessionService adminSessionService,
            AdminLoginRateLimiter loginRateLimiter
    ) {
        this.adminAuthService = adminAuthService;
        this.adminSessionService = adminSessionService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    public AdminAccountResponse login(
            @Valid @RequestBody AdminLoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String clientIp = httpRequest.getRemoteAddr();

        if (!loginRateLimiter.tryAcquire(clientIp)) {
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "登录请求过于频繁，请稍后再试"
            );
        }

        AdminAccountResponse account =
                adminAuthService.authenticate(
                        request.username(),
                        request.password()
                );

        HttpSession oldSession = httpRequest.getSession(false);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session = httpRequest.getSession(true);

        session.setMaxInactiveInterval(30 * 60);

        session.setAttribute("adminId", account.id());

        return account;
    }

    @GetMapping("/me")
    public AdminAccountResponse getCurrentAdmin(
            HttpServletRequest httpRequest
    ) {
        return adminSessionService.requireCurrentAdmin(httpRequest);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest) {
        HttpSession session = httpRequest.getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }
}