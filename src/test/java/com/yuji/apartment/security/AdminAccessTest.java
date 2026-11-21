package com.yuji.apartment.security;

import com.yuji.apartment.config.SecurityConfig;
import com.yuji.apartment.controller.ApartmentController;
import com.yuji.apartment.service.AdminAuthService;
import com.yuji.apartment.service.AdminSessionService;
import com.yuji.apartment.service.UserSessionService;
import com.yuji.apartment.service.ApartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.yuji.apartment.dto.AdminAccountResponse;
import com.yuji.apartment.dto.UserAccountResponse;
import com.yuji.apartment.dto.ApartmentResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApartmentController.class)
@EnableWebSecurity
@Import({SecurityConfig.class, AdminSessionService.class})
class AdminAccessTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApartmentService apartmentService;

    @MockitoBean
    private AdminAuthService adminAuthService;

    @MockitoBean
    private UserSessionService userSessionService;

    @Test
    void regularUserCannotAccessAdministratorData() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("userId", 7L);
        when(userSessionService.requireCurrentUser(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new UserAccountResponse(7L, "tenant", "用户"));
        mockMvc.perform(get("/api/apartments/101").session(session)).andExpect(status().isForbidden());
        verifyNoInteractions(apartmentService);
    }

    @Test
    void shouldRejectApartmentRequestWithoutLogin() throws Exception {
        mockMvc.perform(get("/api/apartments/101"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(apartmentService, adminAuthService);
    }

    @Test
    void shouldAllowApartmentRequestWithAdminSession() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminId", 1L);

        when(adminAuthService.findEnabledAccountById(1L))
                .thenReturn(new AdminAccountResponse(1L, "admin"));

        when(apartmentService.findById(101L))
                .thenReturn(new ApartmentResponse(
                        101L,
                        "青年公寓",
                        "长春",
                        2500
                ));

        mockMvc.perform(get("/api/apartments/101")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.name").value("青年公寓"));

        verify(adminAuthService).findEnabledAccountById(1L);
        verify(apartmentService).findById(101L);
    }

    @Test
    void shouldRejectApartmentRequestWhenAdminAccountIsInvalid()
            throws Exception {

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("adminId", 1L);

        when(adminAuthService.findEnabledAccountById(1L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "登录状态已失效，请重新登录"
                ));

        mockMvc.perform(get("/api/apartments/101")
                        .session(session))
                .andExpect(status().isUnauthorized());

        verify(adminAuthService).findEnabledAccountById(1L);
        verifyNoInteractions(apartmentService);
    }
}
