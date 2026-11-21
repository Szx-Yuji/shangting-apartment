package com.yuji.apartment.service;

import com.yuji.apartment.dto.AdminAccountResponse;
import com.yuji.apartment.entity.AdminUser;
import com.yuji.apartment.mapper.AdminUserMapper;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class AdminAuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public AdminAuthService(
            AdminUserMapper adminUserMapper,
            PasswordEncoder passwordEncoder
    ) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.dummyPasswordHash = passwordEncoder.encode(
                UUID.randomUUID().toString()
        );
    }

    public AdminAccountResponse authenticate(
            String username,
            String password
    ) {
        if (username == null
                || !username.matches("[A-Za-z0-9_]{3,50}")
                || password == null
                || password.isBlank()
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        AdminUser user = adminUserMapper.selectByUsername(username);

        String hash = user == null
                ? dummyPasswordHash
                : user.getPasswordHash();

        boolean passwordMatches = passwordEncoder.matches(password, hash);

        if (user == null
                || !passwordMatches
                || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "用户名或密码错误"
            );
        }

        return new AdminAccountResponse(
                user.getId(),
                user.getUsername()
        );
    }

    public AdminAccountResponse findEnabledAccountById(
            Long adminId
    ) {
        AdminUser user = adminUserMapper.selectById(adminId);

        if (user == null
                || !Boolean.TRUE.equals(user.getEnabled())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "登录状态已失效，请重新登录"
            );
        }

        return new AdminAccountResponse(
                user.getId(),
                user.getUsername()
        );
    }
}