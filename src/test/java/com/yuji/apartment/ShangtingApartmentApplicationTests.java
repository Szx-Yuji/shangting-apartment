package com.yuji.apartment;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = "ADMIN_INIT_ENABLED=false")
@ActiveProfiles("test")
class ShangtingApartmentApplicationTests {

    @Test
    void contextLoads() {
    }
}