package com.yuji.apartment.service;

import com.yuji.apartment.dto.UserAccountResponse;
import com.yuji.apartment.dto.UserRegisterRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class UserAuthService {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    private final String dummyHash;

    public UserAuthService(JdbcClient jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.dummyHash = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserAccountResponse register(UserRegisterRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码UTF-8编码后不能超过72字节");
        }
        try {
            jdbc.sql("INSERT INTO app_user(username,password_hash,display_name,enabled,created_at) VALUES(:username,:hash,:name,true,:created)")
                    .param("username", request.username()).param("hash", encoder.encode(request.password()))
                    .param("name", request.displayName().strip()).param("created", LocalDateTime.now(ZoneOffset.UTC)).update();
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被使用");
        }
        return jdbc.sql("SELECT id,username,display_name FROM app_user WHERE username=:username")
                .param("username", request.username()).query((rs, row) -> new UserAccountResponse(
                        rs.getLong("id"), rs.getString("username"), rs.getString("display_name"))).single();
    }

    public UserAccountResponse authenticate(String username, String password) {
        if (username == null || !username.matches("[A-Za-z0-9_]{3,50}") || password == null
                || password.isBlank() || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw unauthorized();
        }
        Credential user = jdbc.sql("SELECT id,username,display_name,password_hash,enabled FROM app_user WHERE username=:username")
                .param("username", username).query((rs, row) -> new Credential(rs.getLong("id"),
                        rs.getString("username"), rs.getString("display_name"), rs.getString("password_hash"),
                        rs.getBoolean("enabled"))).optional().orElse(null);
        boolean matches = encoder.matches(password, user == null ? dummyHash : user.hash());
        if (user == null || !matches || !user.enabled()) throw unauthorized();
        return new UserAccountResponse(user.id(), user.username(), user.displayName());
    }

    public UserAccountResponse findEnabledAccountById(Long id) {
        return jdbc.sql("SELECT id,username,display_name FROM app_user WHERE id=:id AND enabled=true")
                .param("id", id).query((rs, row) -> new UserAccountResponse(rs.getLong("id"),
                        rs.getString("username"), rs.getString("display_name"))).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录状态已失效，请重新登录"));
    }

    private ResponseStatusException unauthorized() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    private record Credential(Long id, String username, String displayName, String hash, boolean enabled) {
        @Override public String toString() { return "Credential[REDACTED]"; }
    }
}
