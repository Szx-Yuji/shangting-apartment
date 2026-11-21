package com.yuji.apartment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Runs real SQL against a private in-memory database, never the developer's MySQL. */
class AppointmentCancellationTest {
    private JdbcClient jdbc;
    private MyAppointmentService appointments;
    private TransactionTemplate transaction;

    @BeforeEach void setup() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:cancel_" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000", "sa", "");
        jdbc = JdbcClient.create(source);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(source));
        appointments = new MyAppointmentService(jdbc);
        jdbc.sql("CREATE TABLE appointment_slot(id BIGINT PRIMARY KEY,reserved_count INT NOT NULL)").update();
        jdbc.sql("CREATE TABLE appointment(id BIGINT PRIMARY KEY,user_id BIGINT,slot_id BIGINT,status VARCHAR(20),appointment_time TIMESTAMP)").update();
        jdbc.sql("INSERT INTO appointment_slot VALUES(1,1)").update();
        jdbc.sql("INSERT INTO appointment VALUES(1,7,1,'PENDING',:future)")
                .param("future", LocalDateTime.now(ZoneOffset.UTC).plusDays(2)).update();
    }

    @Test void ownerCanCancelOnceAndRetriesDoNotReleaseAgain() {
        transaction.executeWithoutResult(status -> appointments.cancel(7L, 1L));
        transaction.executeWithoutResult(status -> appointments.cancel(7L, 1L));
        assertEquals("CANCELLED", status());
        assertEquals(0, reserved());
    }

    @Test void anotherUserCannotCancelOrDiscoverTheRecord() {
        var exception = assertThrows(ResponseStatusException.class,
                () -> transaction.executeWithoutResult(status -> appointments.cancel(8L, 1L)));
        assertEquals(404, exception.getStatusCode().value());
        assertEquals("PENDING", status());
        assertEquals(1, reserved());
    }

    @Test void capacityFailureRollsBackTheStatusChange() {
        jdbc.sql("UPDATE appointment_slot SET reserved_count=0 WHERE id=1").update();
        var exception = assertThrows(ResponseStatusException.class,
                () -> transaction.executeWithoutResult(status -> appointments.cancel(7L, 1L)));
        assertEquals(500, exception.getStatusCode().value());
        assertEquals("PENDING", status());
        assertEquals(0, reserved());
    }

    @Test void concurrentRejectionAndCancellationNeverReleaseTwice() throws Exception {
        CountDownLatch rejected = new CountDownLatch(1);
        CountDownLatch commit = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var admin = pool.submit(() -> transaction.executeWithoutResult(status -> {
                jdbc.sql("UPDATE appointment SET status='REJECTED' WHERE id=1 AND status='PENDING'").update();
                jdbc.sql("UPDATE appointment_slot SET reserved_count=reserved_count-1 WHERE id=1 AND reserved_count>0").update();
                rejected.countDown();
                try { assertTrue(commit.await(3, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new IllegalStateException(exception); }
            }));
            assertTrue(rejected.await(3, TimeUnit.SECONDS));
            var cancel = pool.submit(() -> assertThrows(ResponseStatusException.class,
                    () -> transaction.executeWithoutResult(status -> appointments.cancel(7L, 1L))));
            commit.countDown();
            admin.get(5, TimeUnit.SECONDS);
            assertEquals(409, cancel.get(5, TimeUnit.SECONDS).getStatusCode().value());
        }
        assertEquals("REJECTED", status());
        assertEquals(0, reserved());
    }

    private String status() { return jdbc.sql("SELECT status FROM appointment WHERE id=1").query(String.class).single(); }
    private int reserved() { return jdbc.sql("SELECT reserved_count FROM appointment_slot WHERE id=1").query(Integer.class).single(); }
}
