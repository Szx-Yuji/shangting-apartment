package com.yuji.apartment.service;

import com.yuji.apartment.dto.AdminAccountResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AdminSessionService {

    private final AdminAuthService adminAuthService;

    public AdminSessionService(
            AdminAuthService adminAuthService
    ) {
        this.adminAuthService = adminAuthService;
    }

    public AdminAccountResponse requireCurrentAdmin(
            HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(false);

        if (session == null || session.getAttribute("userId") != null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }

        Object value = session.getAttribute("adminId");

        if (!(value instanceof Long adminId)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "请先登录"
            );
        }

        return adminAuthService.findEnabledAccountById(adminId);
    }
}
