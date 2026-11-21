package com.yuji.apartment.service;

import com.yuji.apartment.entity.AdminUser;
import com.yuji.apartment.mapper.AdminUserMapper;
import com.yuji.apartment.dto.AdminAccountResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


class AdminAuthServiceTest {

    @Test
    void shouldRejectDisabledAdminAccount() {
        AdminUserMapper adminUserMapper =
                mock(AdminUserMapper.class);

        PasswordEncoder passwordEncoder =
                mock(PasswordEncoder.class);

        AdminAuthService adminAuthService =
                new AdminAuthService(
                        adminUserMapper,
                        passwordEncoder
                );

        AdminUser user = new AdminUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setEnabled(false);

        when(adminUserMapper.selectById(1L))
                .thenReturn(user);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminAuthService.findEnabledAccountById(1L)
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );

        assertEquals(
                "登录状态已失效，请重新登录",
                exception.getReason()
        );

        verify(adminUserMapper).selectById(1L);
    }

    @Test
    void shouldAuthenticateEnabledAdminWithCorrectPassword() {
        AdminUserMapper adminUserMapper =
                mock(AdminUserMapper.class);

        PasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder(12);

        AdminAuthService adminAuthService =
                new AdminAuthService(
                        adminUserMapper,
                        passwordEncoder
                );

        String rawPassword = "UnitTestOnly!2026";

        AdminUser user = new AdminUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setEnabled(true);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));

        when(adminUserMapper.selectByUsername("admin"))
                .thenReturn(user);

        AdminAccountResponse account =
                adminAuthService.authenticate(
                        "admin",
                        rawPassword
                );

        assertEquals(1L, account.id());
        assertEquals("admin", account.username());

        verify(adminUserMapper).selectByUsername("admin");
    }

    @ParameterizedTest
    @CsvSource({
            "true, true, WrongPassword!2026",
            "false, true, UnitTestOnly!2026",
            "true, false, UnitTestOnly!2026"
    })
    void shouldRejectInvalidLogin(
            boolean accountExists,
            boolean enabled,
            String submittedPassword
    ) {
        AdminUserMapper adminUserMapper =
                mock(AdminUserMapper.class);

        PasswordEncoder passwordEncoder =
                new BCryptPasswordEncoder(12);

        AdminAuthService adminAuthService =
                new AdminAuthService(
                        adminUserMapper,
                        passwordEncoder
                );

        AdminUser user = null;

        if (accountExists) {
            user = new AdminUser();
            user.setId(1L);
            user.setUsername("admin");
            user.setEnabled(enabled);
            user.setPasswordHash(
                    passwordEncoder.encode("UnitTestOnly!2026")
            );
        }

        when(adminUserMapper.selectByUsername("admin"))
                .thenReturn(user);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminAuthService.authenticate(
                        "admin",
                        submittedPassword
                )
        );

        assertEquals(
                HttpStatus.UNAUTHORIZED,
                exception.getStatusCode()
        );
        assertEquals("用户名或密码错误", exception.getReason());

        verify(adminUserMapper).selectByUsername("admin");
    }
}