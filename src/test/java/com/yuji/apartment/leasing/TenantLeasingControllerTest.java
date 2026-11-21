package com.yuji.apartment.leasing;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantLeasingControllerTest {
    @Test
    void ownershipAlwaysComesFromServerSession() {
        LeasingService service=mock(LeasingService.class);
        TenantLeasingController controller=new TenantLeasingController(service);
        MockHttpServletRequest request=new MockHttpServletRequest();
        request.getSession().setAttribute("userId",42L);
        request.setParameter("userId","999");
        controller.bills(null,1,10,request);
        controller.pay(8,request);
        verify(service).bills(null,null,42L,1,10);
        verify(service).pay(8,42L,true);
    }

    @Test
    void missingSessionCannotReadTenantData() {
        LeasingService service=mock(LeasingService.class);
        TenantLeasingController controller=new TenantLeasingController(service);
        assertEquals(HttpStatus.UNAUTHORIZED,assertThrows(ResponseStatusException.class,
                ()->controller.notifications(new MockHttpServletRequest())).getStatusCode());
        verifyNoInteractions(service);
    }
}
