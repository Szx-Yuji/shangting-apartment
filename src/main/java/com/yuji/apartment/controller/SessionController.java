package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AdminAccountResponse;
import com.yuji.apartment.dto.SessionResponse;
import com.yuji.apartment.dto.UserAccountResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SessionController {
    @GetMapping("/api/session")
    public SessionResponse current(Authentication authentication) {
        if (authentication == null) return SessionResponse.anonymous();
        if (authentication.getPrincipal() instanceof AdminAccountResponse admin) {
            return new SessionResponse(true, "ADMIN", new SessionResponse.Account(admin.id(), admin.username(), admin.username()));
        }
        if (authentication.getPrincipal() instanceof UserAccountResponse user) {
            return new SessionResponse(true, "USER", new SessionResponse.Account(user.id(), user.username(), user.displayName()));
        }
        return SessionResponse.anonymous();
    }
}
