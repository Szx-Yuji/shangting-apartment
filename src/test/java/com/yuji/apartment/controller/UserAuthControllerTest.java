package com.yuji.apartment.controller;

import com.yuji.apartment.dto.AdminLoginRequest;
import com.yuji.apartment.dto.UserAccountResponse;
import com.yuji.apartment.ratelimit.RedisFixedWindowRateLimiter;
import com.yuji.apartment.service.UserAuthService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class UserAuthControllerTest {
    private final UserAuthService auth = mock(UserAuthService.class);
    private final RedisFixedWindowRateLimiter limiter = mock(RedisFixedWindowRateLimiter.class);
    private final UserAuthController controller = new UserAuthController(auth, limiter);

    @Test void successfulLoginRotatesSessionAndDropsAdministratorIdentity() {
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(auth.authenticate(anyString(), anyString())).thenReturn(new UserAccountResponse(7L, "tenant", "用户"));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession old = new MockHttpSession(); old.setAttribute("adminId", 1L); request.setSession(old);
        String oldId = old.getId();
        controller.login(new AdminLoginRequest("tenant", "synthetic-password"), request);
        assertTrue(old.isInvalid());
        assertNotEquals(oldId, request.getSession().getId());
        assertNull(request.getSession().getAttribute("adminId"));
        assertEquals(7L, request.getSession().getAttribute("userId"));
        assertEquals(1800, request.getSession().getMaxInactiveInterval());
    }

    @Test void deniedLoginNeverChecksPasswordOrDestroysExistingSession() {
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession old = new MockHttpSession(); request.setSession(old);
        var exception = assertThrows(ResponseStatusException.class, () ->
                controller.login(new AdminLoginRequest("tenant", "synthetic-password"), request));
        assertEquals(429, exception.getStatusCode().value());
        assertFalse(old.isInvalid());
        verifyNoInteractions(auth);
    }

    @Test void incorrectCredentialsKeepOldSession() {
        when(limiter.tryAcquire(anyString(), anyInt(), anyInt())).thenReturn(true);
        when(auth.authenticate(anyString(), anyString())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession old = new MockHttpSession(); request.setSession(old);
        assertThrows(ResponseStatusException.class, () -> controller.login(new AdminLoginRequest("tenant", "bad"), request));
        assertFalse(old.isInvalid());
    }
}
