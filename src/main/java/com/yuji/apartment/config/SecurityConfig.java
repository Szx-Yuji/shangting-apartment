package com.yuji.apartment.config;

import com.yuji.apartment.security.AdminSessionAuthenticationFilter;
import com.yuji.apartment.service.AdminSessionService;
import com.yuji.apartment.service.UserSessionService;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class SecurityConfig {
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AdminSessionService admins,
                                                   UserSessionService users) throws Exception {
        http.securityContext(config -> config.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(config -> config.disable())
                .formLogin(config -> config.disable())
                .httpBasic(config -> config.disable())
                .logout(config -> config.disable())
                .headers(headers -> headers.contentSecurityPolicy(policy -> policy.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
                                + "connect-src 'self'; font-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'self'")))
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) -> problem(response, 401, "Unauthorized", "请先登录"))
                        .accessDeniedHandler((request, response, exception) -> problem(response, 403, "Forbidden", "请求未通过安全校验")))
                .authorizeHttpRequests(config -> config
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.ASYNC).permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/csrf", "/api/session",
                                "/api/public/**", "/", "/index.html", "/assets/**", "/images/**", "/uploads/**", "/favicon.svg").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login", "/api/auth/logout",
                                "/api/admin/auth/login", "/api/admin/auth/logout", "/api/public/appointments").permitAll()
                        .requestMatchers("/api/me/**").hasRole("USER")
                        .requestMatchers("/api/admin/**", "/api/apartments/**", "/api/rooms/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .addFilterBefore(new AdminSessionAuthenticationFilter(admins, users), AnonymousAuthenticationFilter.class);
        return http.build();
    }

    private static void problem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/problem+json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status);
        body.put("detail", detail);
        String requestId = MDC.get("requestId");
        if (requestId != null) body.put("requestId", requestId);
        response.getWriter().write(new JsonMapper().writeValueAsString(body));
    }
}
