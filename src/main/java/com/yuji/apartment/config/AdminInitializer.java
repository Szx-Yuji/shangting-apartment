package com.yuji.apartment.config;

import com.yuji.apartment.entity.AdminUser;
import com.yuji.apartment.mapper.AdminUserMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
@ConditionalOnProperty(name = "ADMIN_INIT_ENABLED", havingValue = "true")
public class AdminInitializer implements ApplicationRunner {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminInitializer(
            AdminUserMapper adminUserMapper,
            PasswordEncoder passwordEncoder,
            @Value("${ADMIN_USERNAME}") String username,
            @Value("${ADMIN_PASSWORD}") String password
    ) {
        this.adminUserMapper = adminUserMapper;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!username.matches("[A-Za-z0-9_]{3,50}")) {
            throw new IllegalStateException(
                    "初始化管理员用户名须为3到50位字母、数字或下划线"
            );
        }

        if (password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(
                    "初始化管理员密码长度至少为12，UTF-8编码后不能超过72字节"
            );
        }

        if (adminUserMapper.selectByUsername(username) != null) {
            return;
        }

        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setEnabled(true);

        try {
            adminUserMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            if (adminUserMapper.selectByUsername(username) == null) {
                throw exception;
            }
        }
    }
}