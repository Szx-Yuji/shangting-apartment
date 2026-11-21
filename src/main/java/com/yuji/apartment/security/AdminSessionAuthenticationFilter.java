package com.yuji.apartment.security;

import com.yuji.apartment.dto.AdminAccountResponse;
import com.yuji.apartment.service.AdminSessionService;
import com.yuji.apartment.service.UserSessionService;
import com.yuji.apartment.dto.UserAccountResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;


import java.io.IOException;
import java.util.List;

public class AdminSessionAuthenticationFilter
        extends OncePerRequestFilter {

    private final AdminSessionService adminSessionService;
    private final UserSessionService userSessionService;

    public AdminSessionAuthenticationFilter(
            AdminSessionService adminSessionService
    ) {
        this(adminSessionService, null);
    }

    public AdminSessionAuthenticationFilter(AdminSessionService adminSessionService, UserSessionService userSessionService) {
        this.adminSessionService = adminSessionService;
        this.userSessionService = userSessionService;
    }

    @Override
    protected boolean shouldNotFilter(
            HttpServletRequest request
    ) {
        return "/actuator/health".equals(
                request.getServletPath()
        );
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        SecurityContext context =
                SecurityContextHolder.createEmptyContext();

        try {
            SecurityContextHolder.setContext(context);

            HttpSession session = request.getSession(false);
            if (session != null) {
                Object adminId = session.getAttribute("adminId");
                Object userId = session.getAttribute("userId");
                try {
                    if (adminId instanceof Long && userId == null) {
                        AdminAccountResponse account = adminSessionService.requireCurrentAdmin(request);
                        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                                account, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
                    } else if (userId instanceof Long && adminId == null && userSessionService != null) {
                        UserAccountResponse account = userSessionService.requireCurrentUser(request);
                        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                                account, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
                    }
                } catch (ResponseStatusException exception) {
                    if (exception.getStatusCode().value() != 401) throw exception;
                }
            }

            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
