package com.yuji.apartment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AdminAccountGuardTest {
    private JdbcClient jdbc;
    private AdminAccountService accounts;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:admins_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = JdbcClient.create(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        jdbc.sql("CREATE TABLE admin_user(id BIGINT PRIMARY KEY,enabled BOOLEAN NOT NULL)").update();
        jdbc.sql("INSERT INTO admin_user VALUES(1,true),(2,true)").update();
        accounts = new AdminAccountService(jdbc, mock(PasswordEncoder.class));
    }

    @Test void cannotDisableSelf() {
        var error = assertThrows(ResponseStatusException.class,
                () -> transaction.executeWithoutResult(tx -> accounts.setEnabled(1L,1L,false)));
        assertEquals(409,error.getStatusCode().value());
        assertEquals(2L,jdbc.sql("SELECT COUNT(*) FROM admin_user WHERE enabled=true").query(Long.class).single());
    }

    @Test void disabledActorCannotDisableTheLastActiveAccount() {
        transaction.executeWithoutResult(tx -> accounts.setEnabled(1L,2L,false));
        var error = assertThrows(ResponseStatusException.class,
                () -> transaction.executeWithoutResult(tx -> accounts.setEnabled(2L,1L,false)));
        assertEquals(401,error.getStatusCode().value());
        assertEquals(1L,jdbc.sql("SELECT COUNT(*) FROM admin_user WHERE enabled=true").query(Long.class).single());
    }
}
