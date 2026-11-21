package com.yuji.apartment.leasing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yuji.apartment.leasing.LeasingDtos.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class LeaseMaintenanceJobTest {
    @Test
    void maintenanceBatchHasAHardUpperBoundAndAccumulatesResults() {
        LeasingRepository repository=mock(LeasingRepository.class);
        LeasingService service=mock(LeasingService.class);
        LeaseMaintenanceJob job=new LeaseMaintenanceJob(repository,service,100000,true);
        when(repository.maintenanceLeaseIds(any(),eq(500))).thenReturn(List.of(7L,8L));
        when(service.maintainLease(eq(7L),any())).thenReturn(new MaintenanceResult(1,1,0,0));
        when(service.maintainLease(eq(8L),any())).thenReturn(new MaintenanceResult(1,0,1,0));
        when(repository.markOverdue(any(),eq(500))).thenReturn(3);
        assertEquals(new MaintenanceResult(2,1,1,3),job.run());
    }

    @Test
    void disablingScheduledJobDoesNotDisableExplicitAdminRun() {
        LeasingRepository repository=mock(LeasingRepository.class);
        LeasingService service=mock(LeasingService.class);
        LeaseMaintenanceJob job=new LeaseMaintenanceJob(repository,service,200,false);
        job.scheduled();
        verifyNoInteractions(repository,service);
        when(repository.maintenanceLeaseIds(any(),eq(200))).thenReturn(List.of());
        job.run();
        verify(repository).markOverdue(any(),eq(200));
    }
}
