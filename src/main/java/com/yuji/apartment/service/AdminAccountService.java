package com.yuji.apartment.service;

import com.yuji.apartment.dto.AccountPageResponse;
import com.yuji.apartment.dto.AdminAccountCreateRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class AdminAccountService {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    public AdminAccountService(JdbcClient jdbc, PasswordEncoder encoder) { this.jdbc = jdbc; this.encoder = encoder; }

    @Transactional(readOnly = true)
    public AccountPageResponse users(long page, long pageSize) {
        validatePage(page, pageSize);
        long total = jdbc.sql("SELECT COUNT(*) FROM app_user").query(Long.class).single();
        var records = jdbc.sql("SELECT id,username,display_name,enabled,created_at FROM app_user ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> account(rs, rs.getString("display_name"))).list();
        return new AccountPageResponse(records, total, page, pageSize);
    }

    @Transactional(readOnly = true)
    public AccountPageResponse admins(long page, long pageSize) {
        validatePage(page, pageSize);
        long total = jdbc.sql("SELECT COUNT(*) FROM admin_user").query(Long.class).single();
        var records = jdbc.sql("SELECT id,username,enabled,created_at FROM admin_user ORDER BY id DESC LIMIT :limit OFFSET :offset")
                .param("limit", pageSize).param("offset", (page - 1) * pageSize)
                .query((rs, row) -> account(rs, rs.getString("username"))).list();
        return new AccountPageResponse(records, total, page, pageSize);
    }

    @Transactional
    public AccountPageResponse.Account create(AdminAccountCreateRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "密码UTF-8编码后不能超过72字节");
        }
        try {
            jdbc.sql("INSERT INTO admin_user(username,password_hash,enabled,created_at) VALUES(:username,:hash,true,:created)")
                    .param("username", request.username()).param("hash", encoder.encode(request.password()))
                    .param("created", LocalDateTime.now(ZoneOffset.UTC)).update();
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "用户名已被使用");
        }
        return jdbc.sql("SELECT id,username,enabled,created_at FROM admin_user WHERE username=:username")
                .param("username", request.username()).query((rs, row) -> account(rs, rs.getString("username"))).single();
    }

    @Transactional
    public void setEnabled(Long actorId, Long targetId, boolean enabled) {
        if (targetId <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "管理员ID必须大于0");
        List<AdminState> records = jdbc.sql("SELECT id,enabled FROM admin_user ORDER BY id FOR UPDATE")
                .query((rs, row) -> new AdminState(rs.getLong("id"), rs.getBoolean("enabled"))).list();
        if (records.stream().noneMatch(row -> row.id().equals(actorId) && row.enabled())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "管理员登录状态已失效");
        }
        AdminState target = records.stream().filter(row -> row.id().equals(targetId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "管理员不存在"));
        if (!enabled && actorId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "不能禁用当前登录的管理员");
        }
        if (!enabled && target.enabled() && records.stream().filter(AdminState::enabled).count() <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "至少保留一位启用的管理员");
        }
        jdbc.sql("UPDATE admin_user SET enabled=:enabled WHERE id=:id").param("enabled", enabled).param("id", targetId).update();
    }

    public void setUserEnabled(Long id, boolean enabled) {
        if (id <= 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户ID必须大于0");
        // Count first so a repeated setting remains idempotent even on clients reporting only changed rows.
        if (jdbc.sql("SELECT COUNT(*) FROM app_user WHERE id=:id").param("id", id).query(Long.class).single() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        jdbc.sql("UPDATE app_user SET enabled=:enabled WHERE id=:id").param("enabled", enabled).param("id", id).update();
    }

    private AccountPageResponse.Account account(ResultSet rs, String displayName) throws SQLException {
        return new AccountPageResponse.Account(rs.getLong("id"), rs.getString("username"), displayName,
                rs.getBoolean("enabled"), rs.getObject("created_at", LocalDateTime.class).atOffset(ZoneOffset.UTC));
    }

    private void validatePage(long page, long size) {
        if (page < 1 || page > 1_000_000 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数不合法");
        }
    }
    private record AdminState(Long id, boolean enabled) {}
}
