package com.yuji.apartment.leasing;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static com.yuji.apartment.leasing.LeasingDtos.*;

@RestController
@RequestMapping("/api/me")
public class TenantLeasingController {
    private final LeasingService service;

    public TenantLeasingController(LeasingService service) { this.service=service; }

    @GetMapping("/leases")
    public Page<LeaseView> leases(
            @RequestParam(name="status",required=false) String status,
            @RequestParam(name="page",defaultValue="1") long page,
            @RequestParam(name="pageSize",defaultValue="10") long pageSize,
            HttpServletRequest request) {
        return service.leases(status,null,userId(request),page,pageSize);
    }

    @GetMapping("/bills")
    public Page<BillView> bills(
            @RequestParam(name="status",required=false) String status,
            @RequestParam(name="page",defaultValue="1") long page,
            @RequestParam(name="pageSize",defaultValue="10") long pageSize,
            HttpServletRequest request) {
        return service.bills(status,null,userId(request),page,pageSize);
    }

    @PostMapping("/bills/{id}/pay")
    public BillView pay(@PathVariable("id") long id,HttpServletRequest request) {
        return service.pay(id,userId(request),true);
    }

    @GetMapping("/notifications")
    public List<NotificationView> notifications(HttpServletRequest request) {
        return service.notifications(userId(request));
    }

    @PatchMapping("/notifications/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable("id") long id,HttpServletRequest request) {
        service.readNotification(id,userId(request));
    }

    private long userId(HttpServletRequest request) {
        HttpSession session=request.getSession(false);
        Object value=session==null?null:session.getAttribute("userId");
        if (!(value instanceof Long id) || id<=0) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请先登录租客账号");
        }
        return id;
    }
}
