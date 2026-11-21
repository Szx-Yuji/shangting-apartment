package com.yuji.apartment.security;

import com.yuji.apartment.dto.AdminAccountResponse;
import com.yuji.apartment.dto.UserAccountResponse;
import com.yuji.apartment.service.AdminSessionService;
import com.yuji.apartment.service.UserSessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionRoleIsolationTest {
    private final AdminSessionService admins = mock(AdminSessionService.class);
    private final UserSessionService users = mock(UserSessionService.class);
    private final AdminSessionAuthenticationFilter filter = new AdminSessionAuthenticationFilter(admins, users);

    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void userAndAdministratorPrincipalsHaveSeparateRolesAndAreClearedAfterRequest() throws Exception {
        MockHttpServletRequest request = request("userId", 12L);
        UserAccountResponse user = new UserAccountResponse(12L, "tenant12", "租客");
        when(users.requireCurrentUser(request)).thenReturn(user);
        filter.doFilter(request, new MockHttpServletResponse(), (req, response) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            assertSame(user, auth.getPrincipal());
            assertEquals("ROLE_USER", auth.getAuthorities().iterator().next().getAuthority());
        });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(admins);

        MockHttpServletRequest adminRequest = request("adminId", 9L);
        when(admins.requireCurrentAdmin(adminRequest)).thenReturn(new AdminAccountResponse(9L, "manager"));
        filter.doFilter(adminRequest, new MockHttpServletResponse(), (req, response) -> {
            assertEquals("ROLE_ADMIN", SecurityContextHolder.getContext().getAuthentication()
                    .getAuthorities().iterator().next().getAuthority());
        });
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void ambiguousSessionNeverGrantsEitherRole() throws Exception {
        MockHttpServletRequest request = request("adminId", 1L);
        request.getSession().setAttribute("userId", 1L);
        filter.doFilter(request, new MockHttpServletResponse(), (req, response) ->
                assertNull(SecurityContextHolder.getContext().getAuthentication()));
        verifyNoInteractions(admins, users);
    }

    private MockHttpServletRequest request(String attribute, Long id) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/session");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(attribute, id);
        request.setSession(session);
        return request;
    }
}
