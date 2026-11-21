package com.yuji.apartment.leasing;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import static com.yuji.apartment.leasing.LeasingDtos.*;

/** All routes are protected by the application's /api/admin/** ADMIN rule. */
@RestController
@RequestMapping("/api/admin")
public class AdminLeasingController {
    private final LeasingService service;
    private final LeaseMaintenanceJob maintenance;

    public AdminLeasingController(LeasingService service,LeaseMaintenanceJob maintenance) {
        this.service=service; this.maintenance=maintenance;
    }

    @GetMapping("/leases")
    public Page<LeaseView> leases(
            @RequestParam(name="status",required=false) String status,
            @RequestParam(name="roomId",required=false) Long roomId,
            @RequestParam(name="page",defaultValue="1") long page,
            @RequestParam(name="pageSize",defaultValue="10") long pageSize) {
        return service.leases(status,roomId,null,page,pageSize);
    }

    @PostMapping("/leases")
    @ResponseStatus(HttpStatus.CREATED)
    public LeaseView create(@Valid @RequestBody CreateLease request) { return service.create(request); }

    @PostMapping("/leases/{id}/activate")
    public LeaseView activate(@PathVariable("id") long id) { return service.activate(id); }

    @PostMapping("/leases/{id}/terminate")
    public LeaseView terminate(@PathVariable("id") long id) { return service.terminate(id); }

    @GetMapping("/bills")
    public Page<BillView> bills(
            @RequestParam(name="status",required=false) String status,
            @RequestParam(name="leaseId",required=false) Long leaseId,
            @RequestParam(name="page",defaultValue="1") long page,
            @RequestParam(name="pageSize",defaultValue="10") long pageSize) {
        return service.bills(status,leaseId,null,page,pageSize);
    }

    @PostMapping("/bills/{id}/mark-paid")
    public BillView markPaid(@PathVariable("id") long id) { return service.pay(id,null,false); }

    @GetMapping("/dashboard")
    public Dashboard dashboard() { return service.dashboard(); }

    @PostMapping("/maintenance/run")
    public MaintenanceResult runMaintenance() { return maintenance.run(); }
}
