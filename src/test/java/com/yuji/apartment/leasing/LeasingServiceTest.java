package com.yuji.apartment.leasing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static com.yuji.apartment.leasing.LeasingDtos.*;
import static com.yuji.apartment.leasing.LeasingRepository.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LeasingServiceTest {
    LeasingRepository repository;
    LeasingService service;
    LocalDate start;
    LeaseData draft;

    @BeforeEach
    void setUp() {
        repository=mock(LeasingRepository.class);
        service=new LeasingService(repository,true);
        start=LeasingService.today().plusDays(1);
        draft=new LeaseData(7,3,42,start,start.plusMonths(12),
                new BigDecimal("2500.00"),new BigDecimal("2500.00"),"DRAFT");
    }

    void locked(LeaseData lease) {
        when(repository.findLease(lease.id())).thenReturn(Optional.of(lease));
        when(repository.lockRoom(lease.roomId())).thenReturn(Optional.of(new RoomData(3,new BigDecimal("2500.00"),true)));
        when(repository.lockLease(lease.id())).thenReturn(Optional.of(lease));
    }

    @Test
    void overlappingLeaseIsRejectedAfterRoomLock() {
        when(repository.lockRoom(3)).thenReturn(Optional.of(new RoomData(3,new BigDecimal("2500.00"),true)));
        when(repository.enabledUser(42)).thenReturn(true);
        when(repository.overlaps(3,start,start.plusMonths(12),null)).thenReturn(true);
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.create(
                new CreateLease(3L,42L,start,start.plusMonths(12),new BigDecimal("2500.00"))));
        assertEquals(HttpStatus.CONFLICT,error.getStatusCode());
        verify(repository,never()).insertLease(any(),any(),any());
    }

    @Test
    void activateCreatesOneBillAndNotificationInLockOrder() {
        locked(draft);
        when(repository.enabledUser(42)).thenReturn(true);
        when(repository.transitionLease(eq(7L),eq("DRAFT"),eq("ACTIVE"),any())).thenReturn(1);
        service.activate(7);
        var order=inOrder(repository);
        order.verify(repository).findLease(7);
        order.verify(repository).lockRoom(3);
        order.verify(repository).lockLease(7);
        order.verify(repository).enabledUser(42);
        order.verify(repository).overlaps(3,start,start.plusMonths(12),7L);
        order.verify(repository).transitionLease(eq(7L),eq("DRAFT"),eq("ACTIVE"),any());
        order.verify(repository).insertBillIfMissing(eq(draft),eq(start),any());
        order.verify(repository).notifyUser(eq(42L),eq("租约已生效"),contains("月租加押金"),any());
    }

    @Test
    void reactivatingActiveLeaseDoesNotGenerateDuplicateBill() {
        LeaseData active=new LeaseData(7,3,42,start,start.plusMonths(12),draft.monthlyRent(),draft.deposit(),"ACTIVE");
        locked(active);
        service.activate(7);
        verify(repository,never()).transitionLease(anyLong(),anyString(),anyString(),any());
        verify(repository,never()).insertBillIfMissing(any(),any(),any());
        verify(repository,never()).notifyUser(anyLong(),anyString(),anyString(),any());
    }

    @Test
    void paymentRejectsAnotherTenantsBillBeforeTakingLocks() {
        when(repository.findBill(9)).thenReturn(Optional.of(new BillData(9,7,42,"UNPAID")));
        ResponseStatusException error=assertThrows(ResponseStatusException.class,()->service.pay(9,99L,true));
        assertEquals(HttpStatus.NOT_FOUND,error.getStatusCode());
        verify(repository,never()).lockLease(anyLong());
        verify(repository,never()).payBill(anyLong(),anyString(),any());
    }

    @Test
    void repeatedPaymentReturnsExistingReceiptWithoutAnotherWrite() {
        LeaseData active=new LeaseData(7,3,42,start,start.plusMonths(12),draft.monthlyRent(),draft.deposit(),"ACTIVE");
        BillData bill=new BillData(9,7,42,"PAID");
        when(repository.findBill(9)).thenReturn(Optional.of(bill));
        when(repository.lockLease(7)).thenReturn(Optional.of(active));
        when(repository.lockBill(9)).thenReturn(Optional.of(bill));
        service.pay(9,42L,true);
        verify(repository,never()).payBill(anyLong(),anyString(),any());
        verify(repository,never()).notifyUser(anyLong(),anyString(),anyString(),any());
    }

    @Test
    void unpaidBillUsesServerAmountAndIsMarkedAsDemo() {
        LeaseData active=new LeaseData(7,3,42,start,start.plusMonths(12),draft.monthlyRent(),draft.deposit(),"ACTIVE");
        BillData bill=new BillData(9,7,42,"UNPAID");
        when(repository.findBill(9)).thenReturn(Optional.of(bill));
        when(repository.lockLease(7)).thenReturn(Optional.of(active));
        when(repository.lockBill(9)).thenReturn(Optional.of(bill));
        when(repository.payBill(eq(9L),eq("DEMO"),any())).thenReturn(1);
        service.pay(9,42L,true);
        var order=inOrder(repository);
        order.verify(repository).findBill(9);
        order.verify(repository).lockLease(7);
        order.verify(repository).lockBill(9);
        order.verify(repository).payBill(eq(9L),eq("DEMO"),any());
        order.verify(repository).notifyUser(eq(42L),eq("账单已结清"),contains("不会真实扣款"),any());
        verify(repository,never()).lockRoom(anyLong());
    }

    @Test
    void demoPaymentsCanBeDisabledWithoutTouchingLedger() {
        LeasingService disabled=new LeasingService(repository,false);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE,assertThrows(ResponseStatusException.class,
                ()->disabled.pay(9,42L,true)).getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void terminatingCancelsUnpaidBillsAndRecordsInboxMessage() {
        locked(draft);
        when(repository.transitionLease(eq(7L),eq("DRAFT"),eq("TERMINATED"),any())).thenReturn(1);
        service.terminate(7);
        verify(repository).cancelUnpaidBills(7);
        verify(repository).notifyUser(eq(42L),eq("租约已终止"),contains("已支付记录保留"),any());
    }

    @Test
    void tenantCannotMarkAnotherTenantsNotificationRead() {
        assertEquals(HttpStatus.NOT_FOUND,assertThrows(ResponseStatusException.class,
                ()->service.readNotification(8,42)).getStatusCode());
        verify(repository,never()).readNotification(anyLong(),anyLong());
    }

    @Test
    void monthEndBillingUsesOriginalAnniversaryWithoutDrift() {
        LocalDate original=LocalDate.of(2026,1,31);
        assertEquals(LocalDate.of(2026,2,28),LeasingService.currentCycleStart(original,LocalDate.of(2026,2,28)));
        assertEquals(LocalDate.of(2026,2,28),LeasingService.currentCycleStart(original,LocalDate.of(2026,3,30)));
        assertEquals(LocalDate.of(2026,3,31),LeasingService.currentCycleStart(original,LocalDate.of(2026,3,31)));
    }

    @Test
    void outOfRangePaginationFailsBeforeQueryingRepository() {
        assertThrows(ResponseStatusException.class,()->service.leases(null,null,null,0,10));
        assertThrows(ResponseStatusException.class,()->service.bills(null,null,42L,1,101));
        assertThrows(ResponseStatusException.class,()->service.leases("UNKNOWN",null,null,1,10));
        verifyNoInteractions(repository);
    }

    @Test
    void expiredLeaseDoesNotGenerateABillOnItsExclusiveEndDate() {
        LocalDate end=LeasingService.today();
        LeaseData active=new LeaseData(7,3,42,end.minusMonths(12),end,
                draft.monthlyRent(),draft.deposit(),"ACTIVE");
        locked(active);
        when(repository.latestBillCycle(7)).thenReturn(Optional.of(end.minusMonths(1)));
        when(repository.transitionLease(eq(7L),eq("ACTIVE"),eq("EXPIRED"),any())).thenReturn(1);
        MaintenanceResult result=service.maintainLease(7,end);
        assertEquals(1,result.expiredLeases());
        assertEquals(0,result.generatedBills());
        verify(repository,never()).insertBillIfMissing(any(),any(),any());
    }

    @Test
    void catchUpHasTwelvePeriodLimitAndKeepsOriginalAnniversary() {
        LocalDate original=LocalDate.of(2025,1,31);
        LeaseData active=new LeaseData(7,3,42,original,original.plusMonths(24),
                draft.monthlyRent(),draft.deposit(),"ACTIVE");
        locked(active);
        when(repository.latestBillCycle(7)).thenReturn(Optional.of(original));
        when(repository.insertBillIfMissing(eq(active),any(),any())).thenReturn(true);
        MaintenanceResult result=service.maintainLease(7,LocalDate.of(2026,9,1));
        assertEquals(12,result.generatedBills());
        ArgumentCaptor<LocalDate> dates=ArgumentCaptor.forClass(LocalDate.class);
        verify(repository,times(12)).insertBillIfMissing(eq(active),dates.capture(),any());
        assertEquals(original.plusMonths(1),dates.getAllValues().getFirst());
        assertEquals(original.plusMonths(12),dates.getAllValues().getLast());
    }

    @Test
    void expiredLeaseContinuesHistoricalCatchUpOnTheNextRun() {
        LocalDate original=LocalDate.of(2025,1,31);
        LeaseData expired=new LeaseData(7,3,42,original,original.plusMonths(18),
                draft.monthlyRent(),draft.deposit(),"EXPIRED");
        locked(expired);
        when(repository.latestBillCycle(7)).thenReturn(Optional.of(original.plusMonths(12)));
        when(repository.insertBillIfMissing(eq(expired),any(),any())).thenReturn(true);
        MaintenanceResult result=service.maintainLease(7,LocalDate.of(2026,9,1));
        assertEquals(5,result.generatedBills());
        verify(repository,never()).transitionLease(anyLong(),anyString(),anyString(),any());
    }

    @Test
    void expiredLeasesCanStillSettleTheirOutstandingBills() {
        LeaseData expired=new LeaseData(7,3,42,start.minusMonths(12),start.minusDays(1),
                draft.monthlyRent(),draft.deposit(),"EXPIRED");
        BillData bill=new BillData(9,7,42,"OVERDUE");
        when(repository.findBill(9)).thenReturn(Optional.of(bill));
        when(repository.lockLease(7)).thenReturn(Optional.of(expired));
        when(repository.lockBill(9)).thenReturn(Optional.of(bill));
        when(repository.payBill(eq(9L),eq("DEMO"),any())).thenReturn(1);
        service.pay(9,42L,true);
        verify(repository).payBill(eq(9L),eq("DEMO"),any());
    }

    @Test
    void paymentAfterTerminationCannotReviveCancelledDebt() {
        LeaseData terminated=new LeaseData(7,3,42,start,start.plusMonths(12),
                draft.monthlyRent(),draft.deposit(),"TERMINATED");
        BillData cancelled=new BillData(9,7,42,"CANCELLED");
        when(repository.findBill(9)).thenReturn(Optional.of(cancelled));
        when(repository.lockLease(7)).thenReturn(Optional.of(terminated));
        when(repository.lockBill(9)).thenReturn(Optional.of(cancelled));
        assertEquals(HttpStatus.CONFLICT,assertThrows(ResponseStatusException.class,
                ()->service.pay(9,42L,true)).getStatusCode());
        verify(repository,never()).payBill(anyLong(),anyString(),any());
    }
}
