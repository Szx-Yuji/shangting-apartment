package com.yuji.apartment.leasing;

import com.yuji.apartment.dto.AppointmentResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** API contracts for leases, ledger bills and the tenant inbox. Money is expressed in CNY. */
public final class LeasingDtos {
    private LeasingDtos() {}

    public record Page<T>(List<T> records, long total, long page, long pageSize) {}

    public record CreateLease(
            @NotNull @Positive Long roomId,
            @NotNull @Positive Long userId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal deposit
    ) {}

    public record LeaseView(
            Long id, Long roomId, String roomNumber, String apartmentName,
            Long userId, String username, LocalDate startDate, LocalDate endDate,
            BigDecimal monthlyRent, BigDecimal deposit, String status, OffsetDateTime createdAt
    ) {}

    public record BillView(
            Long id, Long leaseId, Long userId, String roomNumber, String apartmentName,
            String period, LocalDate dueDate, BigDecimal amount, String status,
            OffsetDateTime paidAt, OffsetDateTime createdAt
    ) {}

    public record NotificationView(
            Long id, String title, String content, boolean read, OffsetDateTime createdAt
    ) {}

    public record CityStat(String city, long roomCount) {}

    public record Dashboard(
            long apartmentCount, long roomCount, long publishedRoomCount,
            long pendingAppointmentCount, long activeLeaseCount, long unpaidBillCount,
            BigDecimal monthlyRevenue, List<CityStat> cityStats,
            List<AppointmentResponse> recentAppointments
    ) {}

    public record MaintenanceResult(int checkedLeases, int expiredLeases,
                                    int generatedBills, int overdueBills) {}
}
