package com.yuji.apartment.controller;

import com.yuji.apartment.dto.ApartmentResponse;
import com.yuji.apartment.service.ApartmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(ApartmentController.class)
class ApartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApartmentService apartmentService;

    @Test
    void shouldReturnApartmentById() throws Exception {
        when(apartmentService.findById(101L))
                .thenReturn(new ApartmentResponse(
                        101L,
                        "青年公寓",
                        "长春",
                        2500
                ));

        mockMvc.perform(get("/api/apartments/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.name").value("青年公寓"));
    }

    @Test
    void shouldReturnNotFoundForMissingApartment() throws Exception {
        when(apartmentService.findById(999L))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "公寓不存在: 999"
                ));

        mockMvc.perform(get("/api/apartments/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail")
                        .value("公寓不存在: 999"));
    }

    @Test
    void shouldRejectInvalidApartment() throws Exception {
        String requestBody = """
            {
                "name": "",
                "city": "长春",
                "monthlyRent": -1
            }
            """;

        mockMvc.perform(post("/api/apartments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name")
                        .value("公寓名称不能为空"))
                .andExpect(jsonPath("$.errors.monthlyRent")
                        .value("月租金必须大于0"));

        verifyNoInteractions(apartmentService);
    }
}