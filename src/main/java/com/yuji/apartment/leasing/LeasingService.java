package com.yuji.apartment.leasing;

import com.yuji.apartment.monitor.TimedOperation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static com.yuji.apartment.leasing.LeasingDtos.*;
import static com.yuji.apartment.leasing.LeasingRepository.*;

/**
 * Rental state transitions. Lock order is room -> lease -> bill; notifications are
 * persisted in the same transaction so the inbox cannot announce a rolled-back action.
 */
@Service
public class LeasingService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final LeasingRepository repository;
    private final boolean demoPaymentsEnabled;

    public LeasingService(LeasingRepository repository,
                          @Value("${app.demo-payments-enabled:true}") boolean demoPaymentsEnabled) {
        this.repository = repository;
        this.demoPaymentsEnabled = demoPaymentsEnabled;
    }

    @TimedOperation
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaseView create(CreateLease request) {
        LocalDate today = today();
        if (request.startDate().isBefore(today) || !request.startDate().isBefore(request.endDate())) {
            throw bad("起租日期不能早于今天，退租日期必须晚于起租日期");
        }
        if (request.endDate().getYear()>9999) throw bad("租期日期超出数据库支持范围");
        RoomData room = repository.lockRoom(request.roomId()).orElseThrow(() -> missing("房间不存在"));
        if (!room.published()) throw conflict("房间尚未上架，不能签订租约");
        if (room.monthlyRent().add(request.deposit()).compareTo(new BigDecimal("99999999.99"))>0) {
            throw bad("月租和押金合计超出支持金额范围");
        }
        if (!repository.enabledUser(request.userId())) throw missing("租客不存在或已停用");
        if (repository.overlaps(room.id(), request.startDate(), request.endDate(), null)) {
            throw conflict("该房间在所选租期已有待生效或生效中的租约");
        }
        long id = repository.insertLease(request, room.monthlyRent(), now());
        return repository.leaseView(id);
    }

    @TimedOperation
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaseView activate(long id) {
        LeaseData lease = lockRoomAndLease(id);
        if ("ACTIVE".equals(lease.status())) return repository.leaseView(id);
        if (!"DRAFT".equals(lease.status())) throw conflict("只有待生效租约可以激活");
        if (!lease.endDate().isAfter(today())) throw conflict("租期已结束，不能激活");
        if (!repository.enabledUser(lease.userId())) throw conflict("租客已停用，不能激活租约");
        if (repository.overlaps(lease.roomId(),lease.startDate(),lease.endDate(),id)) {
            throw conflict("房间租期发生冲突，请重新检查");
        }
        LocalDateTime now = now();
        if (repository.transitionLease(id,"DRAFT","ACTIVE",now) != 1) throw conflict("租约已被其他操作更新");
        repository.insertBillIfMissing(lease,lease.startDate(),now);
        repository.notifyUser(lease.userId(),"租约已生效",
                "您的租约已生效，首期账单为完整月租加押金。请在我的账单中查看。",now);
        return repository.leaseView(id);
    }

    @TimedOperation
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaseView terminate(long id) {
        LeaseData lease=lockRoomAndLease(id);
        if ("TERMINATED".equals(lease.status())) return repository.leaseView(id);
        if (!Set.of("DRAFT","ACTIVE").contains(lease.status())) throw conflict("该租约无法终止");
        LocalDateTime now=now();
        if (repository.transitionLease(id,lease.status(),"TERMINATED",now) != 1) throw conflict("租约已被其他操作更新");
        repository.cancelUnpaidBills(id);
        repository.notifyUser(lease.userId(),"租约已终止",
                "租约已终止，未支付账单已取消。已支付记录保留，押金和退款请联系管理员处理。",now);
        return repository.leaseView(id);
    }

    @TimedOperation
    @Transactional(readOnly = true)
    public Page<LeaseView> leases(String status,Long roomId,Long ownerId,long page,long pageSize) {
        validatePage(page,pageSize);
        String normalized=normalizeStatus(status,Set.of("DRAFT","ACTIVE","TERMINATED","EXPIRED"));
        if (roomId!=null && roomId<=0) throw bad("房间编号必须大于0");
        return repository.leases(normalized,roomId,ownerId,page,pageSize);
    }

    @TimedOperation
    @Transactional(readOnly = true)
    public Page<BillView> bills(String status,Long leaseId,Long ownerId,long page,long pageSize) {
        validatePage(page,pageSize);
        String normalized=normalizeStatus(status,Set.of("UNPAID","PAID","OVERDUE","CANCELLED"));
        if (leaseId!=null && leaseId<=0) throw bad("租约编号必须大于0");
        return repository.bills(normalized,leaseId,ownerId,page,pageSize);
    }

    @TimedOperation
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BillView pay(long billId,Long ownerId,boolean demo) {
        if (demo && !demoPaymentsEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"演示支付已关闭，请联系管理员线下确认收款");
        }
        BillData candidate=repository.findBill(billId).orElseThrow(()->missing("账单不存在"));
        assertOwner(candidate.userId(),ownerId);
        LeaseData lease=repository.lockLease(candidate.leaseId()).orElseThrow(()->missing("租约不存在"));
        BillData bill=repository.lockBill(billId).orElseThrow(()->missing("账单不存在"));
        assertOwner(bill.userId(),ownerId);
        if ("PAID".equals(bill.status())) return repository.billView(billId);
        if (!Set.of("ACTIVE","EXPIRED").contains(lease.status())
                || !Set.of("UNPAID","OVERDUE").contains(bill.status())) {
            throw conflict("账单已取消或租约已失效，不能支付");
        }
        LocalDateTime now=now();
        if (repository.payBill(billId,demo?"DEMO":"MANUAL",now)!=1) throw conflict("账单已被其他操作更新");
        repository.notifyUser(bill.userId(),"账单已结清",
                demo?"演示支付已完成，本次操作仅更新学习系统账本，不会真实扣款。":"管理员已登记线下收款，账单已结清。",now);
        return repository.billView(billId);
    }

    @Transactional(readOnly = true)
    public List<NotificationView> notifications(long userId) { return repository.notifications(userId); }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void readNotification(long id,long userId) {
        if (!repository.notificationOwned(id,userId)) throw missing("消息不存在");
        repository.readNotification(id,userId);
    }

    @TimedOperation
    @Transactional(readOnly = true)
    public Dashboard dashboard() {
        LocalDate start=today().withDayOfMonth(1);
        LocalDateTime from=LocalDateTime.ofInstant(start.atStartOfDay(BUSINESS_ZONE).toInstant(),ZoneOffset.UTC);
        LocalDateTime to=LocalDateTime.ofInstant(start.plusMonths(1).atStartOfDay(BUSINESS_ZONE).toInstant(),ZoneOffset.UTC);
        return repository.dashboard(from,to);
    }

    /** Called through a different bean by the scheduler: each lease has its own short transaction. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MaintenanceResult maintainLease(long id,LocalDate date) {
        LeaseData lease=lockRoomAndLease(id);
        if (!Set.of("DRAFT","ACTIVE","EXPIRED").contains(lease.status())) return new MaintenanceResult(1,0,0,0);
        LocalDateTime now=now();
        int generated=0;
        if (Set.of("ACTIVE","EXPIRED").contains(lease.status()) && !lease.startDate().isAfter(date)) {
            generated=catchUpBills(lease,date,now);
        }
        if (!"EXPIRED".equals(lease.status()) && !lease.endDate().isAfter(date)) {
            if (repository.transitionLease(id,lease.status(),"EXPIRED",now)==1) {
                if ("DRAFT".equals(lease.status())) repository.cancelUnpaidBills(id);
                repository.notifyUser(lease.userId(),"租约已到期","您的租约已到期，请联系管理员办理退租或续租。",now);
                return new MaintenanceResult(1,1,generated,0);
            }
        }
        repository.touchLease(id,now);
        return new MaintenanceResult(1,0,generated,0);
    }

    private int catchUpBills(LeaseData lease,LocalDate date,LocalDateTime now) {
        var latest=repository.latestBillCycle(lease.id());
        // Expired drafts were never activated and must not acquire rent obligations.
        if ("EXPIRED".equals(lease.status()) && latest.isEmpty()) return 0;
        long nextIndex=latest.map(value -> ChronoUnit.MONTHS.between(
                YearMonth.from(lease.startDate()),YearMonth.from(value))+1).orElse(0L);
        int generated=0;
        for (int checked=0;checked<12;checked++) {
            LocalDate cycle=lease.startDate().plusMonths(nextIndex+checked);
            if (cycle.isAfter(date) || !cycle.isBefore(lease.endDate())) break;
            if (repository.insertBillIfMissing(lease,cycle,now)) generated++;
        }
        if (generated>0) repository.notifyUser(lease.userId(),"新账单已生成",
                "本次生成或补齐了"+generated+"期租金账单，请在我的账单中查看。",now);
        return generated;
    }

    static LocalDate currentCycleStart(LocalDate start,LocalDate date) {
        long months=ChronoUnit.MONTHS.between(YearMonth.from(start),YearMonth.from(date));
        LocalDate candidate=start.plusMonths(months);
        return candidate.isAfter(date)?start.plusMonths(months-1):candidate;
    }

    private LeaseData lockRoomAndLease(long id) {
        if (id<=0) throw bad("租约编号必须大于0");
        // The first snapshot lookup only discovers the immutable room ID.
        // All state/overlap/account/billing checks after the locks use current reads.
        LeaseData candidate=repository.findLease(id).orElseThrow(()->missing("租约不存在"));
        repository.lockRoom(candidate.roomId()).orElseThrow(()->missing("房间不存在"));
        return repository.lockLease(id).orElseThrow(()->missing("租约不存在"));
    }

    private static void assertOwner(long actual,Long requested) {
        if (requested!=null && actual!=requested) throw missing("账单不存在");
    }

    private static String normalizeStatus(String status,Set<String> allowed) {
        if (status==null || status.isBlank()) return null;
        String value=status.strip();
        if (!allowed.contains(value)) throw bad("状态筛选值不合法");
        return value;
    }

    private static void validatePage(long page,long pageSize) {
        if (page<1 || page>1_000_000 || pageSize<1 || pageSize>100) {
            throw bad("页码须为1到1000000，每页条数须为1到100");
        }
    }

    static LocalDate today() { return LocalDate.now(BUSINESS_ZONE); }
    private static LocalDateTime now() { return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
