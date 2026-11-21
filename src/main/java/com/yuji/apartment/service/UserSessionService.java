package com.yuji.apartment.service;

import com.yuji.apartment.dto.UserAccountResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserSessionService {
    private final UserAuthService auth;
    public UserSessionService(UserAuthService auth) { this.auth = auth; }

    public UserAccountResponse requireCurrentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute("adminId") != null
                || !(session.getAttribute("userId") instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录用户账号");
        }
        return auth.findEnabledAccountById(id);
    }
}
