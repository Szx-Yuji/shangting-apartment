package com.yuji.apartment.leasing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.simple.JdbcClient;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Guards against restoring snapshot reads after a room/lease lock was acquired. */
class LeasingRepositoryCurrentReadTest {
    Connection connection;
    PreparedStatement statement;
    ResultSet rows;
    LeasingRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        DataSource source=mock(DataSource.class);
        connection=mock(Connection.class);
        statement=mock(PreparedStatement.class);
        rows=mock(ResultSet.class);
        ResultSetMetaData metadata=mock(ResultSetMetaData.class);
        when(source.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        repository=new LeasingRepository(JdbcClient.create(source));
    }

    String sql() throws Exception {
        ArgumentCaptor<String> capture=ArgumentCaptor.forClass(String.class);
        verify(connection).prepareStatement(capture.capture());
        return capture.getValue().replaceAll("\\s+"," ").strip();
    }

    @Test
    void enabledAccountUsesCurrentReadInsteadOfCountSnapshot() throws Exception {
        when(rows.next()).thenReturn(true,false);
        when(rows.getLong(1)).thenReturn(42L);
        assertTrue(repository.enabledUser(42));
        assertEquals("SELECT id FROM app_user WHERE id=? AND enabled=TRUE FOR SHARE",sql());
    }

    @Test
    void overlapCheckLocksCurrentMatchingLeaseAndKeepsHalfOpenBounds() throws Exception {
        when(rows.next()).thenReturn(true,false);
        when(rows.getLong(1)).thenReturn(9L);
        assertTrue(repository.overlaps(3,LocalDate.of(2026,11,1),LocalDate.of(2026,12,1),7L));
        String query=sql();
        assertTrue(query.contains("start_date < ? AND end_date > ?"));
        assertTrue(query.endsWith("AND id<>? LIMIT 1 FOR UPDATE"));
        assertFalse(query.contains("COUNT("));
    }

    @Test
    void absentOverlapProducesFalseFromEmptyCurrentRead() throws Exception {
        when(rows.next()).thenReturn(false);
        assertFalse(repository.overlaps(3,LocalDate.of(2026,11,1),LocalDate.of(2026,12,1),null));
        assertTrue(sql().endsWith("LIMIT 1 FOR UPDATE"));
    }

    @Test
    void billingProgressReadsAndLocksLatestActualCycle() throws Exception {
        LocalDate last=LocalDate.of(2026,2,28);
        when(rows.next()).thenReturn(true,false);
        when(rows.getObject(1,LocalDate.class)).thenReturn(last);
        assertEquals(last,repository.latestBillCycle(7).orElseThrow());
        assertTrue(sql().endsWith("ORDER BY due_date DESC,id DESC LIMIT 1 FOR UPDATE"));
    }
}
