package com.yuji.apartment.leasing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.concurrent.locks.ReentrantLock;

import static com.yuji.apartment.leasing.LeasingDtos.MaintenanceResult;

/** Bounded, restart-safe maintenance. SQL conditions and unique bill periods make reruns idempotent. */
@Component
public class LeaseMaintenanceJob {
    private static final Logger log=LoggerFactory.getLogger(LeaseMaintenanceJob.class);
    private final LeasingRepository repository;
    private final LeasingService service;
    private final int batchSize;
    private final boolean scheduledEnabled;
    private final ReentrantLock running=new ReentrantLock();

    public LeaseMaintenanceJob(LeasingRepository repository,LeasingService service,
                               @Value("${app.maintenance.batch-size:200}") int batchSize,
                               @Value("${app.jobs.enabled:true}") boolean scheduledEnabled) {
        this.repository=repository; this.service=service;
        this.batchSize=Math.clamp(batchSize,1,500);
        this.scheduledEnabled=scheduledEnabled;
    }

    @Scheduled(cron="${app.maintenance.cron:0 10 0 * * *}",zone="Asia/Shanghai")
    public void scheduled() {
        if (!scheduledEnabled) return;
        MaintenanceResult result=run();
        log.info("租约维护完成 checked={} expired={} generated={} overdue={}",
                result.checkedLeases(),result.expiredLeases(),result.generatedBills(),result.overdueBills());
    }

    public MaintenanceResult run() {
        if (!running.tryLock()) return new MaintenanceResult(0,0,0,0);
        try {
            LocalDate today=LeasingService.today();
            int checked=0,expired=0,generated=0;
            for (Long id:repository.maintenanceLeaseIds(today,batchSize)) {
                MaintenanceResult result=service.maintainLease(id,today);
                checked+=result.checkedLeases(); expired+=result.expiredLeases(); generated+=result.generatedBills();
            }
            int overdue=repository.markOverdue(today,batchSize);
            return new MaintenanceResult(checked,expired,generated,overdue);
        } finally { running.unlock(); }
    }
}
