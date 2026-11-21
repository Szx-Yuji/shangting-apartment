package com.yuji.apartment.leasing;

import com.yuji.apartment.dto.AppointmentResponse;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.yuji.apartment.leasing.LeasingDtos.*;

@Repository
public class LeasingRepository {
    public record RoomData(long id, BigDecimal monthlyRent, boolean published) {}
    public record LeaseData(long id, long roomId, long userId, LocalDate startDate,
                            LocalDate endDate, BigDecimal monthlyRent, BigDecimal deposit, String status) {}
    public record BillData(long id, long leaseId, long userId, String status) {}

    private static final String LEASE_FIELDS = """
            SELECT lc.id, lc.room_id, r.room_number, a.name AS apartment_name,
                   lc.user_id, u.username, lc.start_date, lc.end_date,
                   lc.monthly_rent, lc.deposit, lc.status, lc.created_at
            FROM lease_contract lc
            JOIN room r ON r.id=lc.room_id
            JOIN apartment a ON a.id=r.apartment_id
            JOIN app_user u ON u.id=lc.user_id
            """;
    private static final String BILL_FIELDS = """
            SELECT b.id, b.lease_id, b.user_id, r.room_number, a.name AS apartment_name,
                   b.period, b.due_date, b.amount, b.status, b.paid_at, b.created_at
            FROM bill b JOIN lease_contract lc ON lc.id=b.lease_id
            JOIN room r ON r.id=lc.room_id JOIN apartment a ON a.id=r.apartment_id
            """;
    private final JdbcClient jdbc;

    public LeasingRepository(JdbcClient jdbc) { this.jdbc = jdbc; }

    public Optional<RoomData> lockRoom(long id) {
        return jdbc.sql("SELECT id, monthly_rent, published FROM room WHERE id=:id FOR UPDATE")
                .param("id", id).query((rs, row) -> new RoomData(rs.getLong("id"),
                        rs.getBigDecimal("monthly_rent"), rs.getBoolean("published"))).optional();
    }

    public boolean enabledUser(long userId) {
        // A locking read observes the latest committed account state even if a
        // prior lookup already established this transaction's RR snapshot.
        return jdbc.sql("SELECT id FROM app_user WHERE id=:id AND enabled=TRUE FOR SHARE")
                .param("id", userId).query(Long.class).optional().isPresent();
    }

    public Optional<LeaseData> findLease(long id) { return selectLease(id, false); }
    public Optional<LeaseData> lockLease(long id) { return selectLease(id, true); }

    private Optional<LeaseData> selectLease(long id, boolean lock) {
        return jdbc.sql("""
                SELECT id, room_id, user_id, start_date, end_date, monthly_rent, deposit, status
                FROM lease_contract WHERE id=:id
                """ + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query((rs, row) -> new LeaseData(rs.getLong("id"),
                        rs.getLong("room_id"), rs.getLong("user_id"), rs.getObject("start_date", LocalDate.class),
                        rs.getObject("end_date", LocalDate.class), rs.getBigDecimal("monthly_rent"),
                        rs.getBigDecimal("deposit"), rs.getString("status"))).optional();
    }

    public boolean overlaps(long roomId, LocalDate start, LocalDate end, Long exceptId) {
        return jdbc.sql("""
                SELECT id FROM lease_contract
                WHERE room_id=:room AND status IN ('DRAFT','ACTIVE')
                  AND start_date < :end AND end_date > :start
                """ + (exceptId == null ? "" : " AND id<>:exceptId") + " LIMIT 1 FOR UPDATE")
                .params(overlapParams(roomId, start, end, exceptId)).query(Long.class).optional().isPresent();
    }

    private Map<String, Object> overlapParams(long roomId, LocalDate start, LocalDate end, Long exceptId) {
        Map<String, Object> params = new HashMap<>();
        params.put("room", roomId); params.put("start", start); params.put("end", end);
        if (exceptId != null) params.put("exceptId", exceptId);
        return params;
    }

    public long insertLease(CreateLease request, BigDecimal rent, LocalDateTime now) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.sql("""
                INSERT INTO lease_contract
                (room_id,user_id,start_date,end_date,monthly_rent,deposit,status,created_at,updated_at)
                VALUES (:room,:user,:start,:end,:rent,:deposit,'DRAFT',:now,:now)
                """).param("room", request.roomId()).param("user", request.userId())
                .param("start", request.startDate()).param("end", request.endDate())
                .param("rent", rent).param("deposit", request.deposit()).param("now", now).update(key, "id");
        return key.getKey().longValue();
    }

    public int transitionLease(long id, String expected, String target, LocalDateTime now) {
        return jdbc.sql("UPDATE lease_contract SET status=:target,updated_at=:now WHERE id=:id AND status=:expected")
                .param("id", id).param("expected", expected).param("target", target).param("now", now).update();
    }

    public void touchLease(long id, LocalDateTime now) {
        jdbc.sql("UPDATE lease_contract SET updated_at=:now WHERE id=:id")
                .param("id", id).param("now", now).update();
    }

    public LeaseView leaseView(long id) {
        return jdbc.sql(LEASE_FIELDS + " WHERE lc.id=:id").param("id", id).query(LEASE_VIEW).single();
    }

    public Page<LeaseView> leases(String status, Long roomId, Long ownerId, long page, long pageSize) {
        String where = " WHERE 1=1";
        Map<String,Object> args = new HashMap<>();
        if (status != null) { where += " AND lc.status=:status"; args.put("status", status); }
        if (roomId != null) { where += " AND lc.room_id=:room"; args.put("room", roomId); }
        if (ownerId != null) { where += " AND lc.user_id=:owner"; args.put("owner", ownerId); }
        long total = jdbc.sql("SELECT COUNT(*) FROM lease_contract lc" + where).params(args).query(Long.class).single();
        args.put("limit", pageSize); args.put("offset", (page-1)*pageSize);
        List<LeaseView> records = jdbc.sql(LEASE_FIELDS + where + " ORDER BY lc.id DESC LIMIT :limit OFFSET :offset")
                .params(args).query(LEASE_VIEW).list();
        return new Page<>(records,total,page,pageSize);
    }

    public boolean insertBillIfMissing(LeaseData lease, LocalDate cycle, LocalDateTime now) {
        String period = java.time.YearMonth.from(cycle).toString();
        BigDecimal amount = lease.monthlyRent();
        if (cycle.equals(lease.startDate())) amount = amount.add(lease.deposit());
        int inserted = jdbc.sql("""
                INSERT INTO bill (lease_id,user_id,period,amount,due_date,status,created_at)
                SELECT :lease,:user,:period,:amount,:due,'UNPAID',:now
                WHERE NOT EXISTS (SELECT 1 FROM bill WHERE lease_id=:lease AND period=:period)
                """).param("lease",lease.id()).param("user",lease.userId()).param("period",period)
                .param("amount",amount).param("due",cycle).param("now",now).update();
        return inserted == 1;
    }

    public int cancelUnpaidBills(long leaseId) {
        return jdbc.sql("UPDATE bill SET status='CANCELLED' WHERE lease_id=:id AND status IN ('UNPAID','OVERDUE')")
                .param("id", leaseId).update();
    }

    public Optional<LocalDate> latestBillCycle(long leaseId) {
        return jdbc.sql("""
                SELECT due_date FROM bill WHERE lease_id=:id
                ORDER BY due_date DESC,id DESC LIMIT 1 FOR UPDATE
                """)
                .param("id",leaseId).query((rs,row)->rs.getObject(1,LocalDate.class)).optional();
    }

    public Optional<BillData> findBill(long id) { return selectBill(id,false); }
    public Optional<BillData> lockBill(long id) { return selectBill(id,true); }
    private Optional<BillData> selectBill(long id, boolean lock) {
        return jdbc.sql("SELECT id,lease_id,user_id,status FROM bill WHERE id=:id" + (lock ? " FOR UPDATE" : ""))
                .param("id",id).query((rs,row) -> new BillData(rs.getLong("id"),rs.getLong("lease_id"),
                        rs.getLong("user_id"),rs.getString("status"))).optional();
    }

    public int payBill(long id, String channel, LocalDateTime now) {
        return jdbc.sql("""
                UPDATE bill SET status='PAID',paid_at=:now,payment_channel=:channel
                WHERE id=:id AND status IN ('UNPAID','OVERDUE')
                """).param("id",id).param("channel",channel).param("now",now).update();
    }

    public BillView billView(long id) {
        return jdbc.sql(BILL_FIELDS + " WHERE b.id=:id").param("id",id).query(BILL_VIEW).single();
    }

    public Page<BillView> bills(String status, Long leaseId, Long ownerId, long page, long pageSize) {
        String where = " WHERE 1=1";
        Map<String,Object> args = new HashMap<>();
        if (status != null) { where += " AND b.status=:status"; args.put("status",status); }
        if (leaseId != null) { where += " AND b.lease_id=:lease"; args.put("lease",leaseId); }
        if (ownerId != null) { where += " AND b.user_id=:owner"; args.put("owner",ownerId); }
        long total = jdbc.sql("SELECT COUNT(*) FROM bill b"+where).params(args).query(Long.class).single();
        args.put("limit",pageSize); args.put("offset",(page-1)*pageSize);
        List<BillView> records=jdbc.sql(BILL_FIELDS+where+" ORDER BY b.id DESC LIMIT :limit OFFSET :offset")
                .params(args).query(BILL_VIEW).list();
        return new Page<>(records,total,page,pageSize);
    }

    public void notifyUser(long userId, String title, String content, LocalDateTime now) {
        jdbc.sql("INSERT INTO notification(user_id,title,content,is_read,created_at) VALUES(:user,:title,:content,FALSE,:now)")
                .param("user",userId).param("title",title).param("content",content).param("now",now).update();
    }

    public List<NotificationView> notifications(long userId) {
        return jdbc.sql("SELECT id,title,content,is_read,created_at FROM notification WHERE user_id=:user ORDER BY id DESC LIMIT 100")
                .param("user",userId).query((rs,row)->new NotificationView(rs.getLong("id"),rs.getString("title"),
                        rs.getString("content"),rs.getBoolean("is_read"),utc(rs,"created_at"))).list();
    }

    public boolean notificationOwned(long notificationId,long userId) {
        return jdbc.sql("SELECT COUNT(*) FROM notification WHERE id=:id AND user_id=:user")
                .param("id",notificationId).param("user",userId).query(Long.class).single()==1;
    }

    public void readNotification(long id,long userId) {
        jdbc.sql("UPDATE notification SET is_read=TRUE WHERE id=:id AND user_id=:user")
                .param("id",id).param("user",userId).update();
    }

    public List<Long> maintenanceLeaseIds(LocalDate today, int limit) {
        return jdbc.sql("""
                SELECT lc.id FROM lease_contract lc
                LEFT JOIN (SELECT lease_id,MAX(due_date) AS last_due FROM bill GROUP BY lease_id) billed
                  ON billed.lease_id=lc.id
                WHERE lc.start_date<=:today
                  AND (lc.status IN ('DRAFT','ACTIVE')
                    OR (lc.status='EXPIRED' AND billed.last_due IS NOT NULL
                      AND DATE_ADD(lc.start_date, INTERVAL (
                        PERIOD_DIFF(EXTRACT(YEAR_MONTH FROM billed.last_due),
                                    EXTRACT(YEAR_MONTH FROM lc.start_date)) + 1
                      ) MONTH) < lc.end_date))
                ORDER BY lc.updated_at ASC,lc.id ASC LIMIT :limit
                """).param("today",today).param("limit",limit).query(Long.class).list();
    }

    public int markOverdue(LocalDate today, int limit) {
        return jdbc.sql("UPDATE bill SET status='OVERDUE' WHERE status='UNPAID' AND due_date<:today ORDER BY id LIMIT :limit")
                .param("today",today).param("limit",limit).update();
    }

    public Dashboard dashboard(LocalDateTime monthStart, LocalDateTime nextMonth) {
        List<CityStat> cities = jdbc.sql("""
                SELECT a.city,COUNT(r.id) AS room_count FROM apartment a
                LEFT JOIN room r ON r.apartment_id=a.id GROUP BY a.city ORDER BY room_count DESC,a.city
                """).query((rs,row)->new CityStat(rs.getString("city"),rs.getLong("room_count"))).list();
        List<AppointmentResponse> appointments = jdbc.sql("""
                SELECT ap.id,ap.room_id,ap.contact_name,ap.contact_phone,ap.appointment_time,
                       ap.status,ap.created_at,r.room_number,a.name AS apartment_name
                FROM appointment ap JOIN room r ON r.id=ap.room_id JOIN apartment a ON a.id=r.apartment_id
                ORDER BY ap.created_at DESC,ap.id DESC LIMIT 6
                """).query((rs,row)->new AppointmentResponse(rs.getLong("id"),rs.getLong("room_id"),
                        rs.getString("contact_name"),maskPhone(rs.getString("contact_phone")),utc(rs,"appointment_time"),
                        rs.getString("status"),utc(rs,"created_at"),rs.getString("room_number"),rs.getString("apartment_name"))).list();
        BigDecimal revenue = jdbc.sql("SELECT COALESCE(SUM(amount),0) FROM bill WHERE status='PAID' AND paid_at>=:start AND paid_at<:end")
                .param("start",monthStart).param("end",nextMonth).query(BigDecimal.class).single();
        return new Dashboard(count("SELECT COUNT(*) FROM apartment"),count("SELECT COUNT(*) FROM room"),
                count("SELECT COUNT(*) FROM room WHERE published=TRUE"),
                count("SELECT COUNT(*) FROM appointment WHERE status='PENDING'"),
                count("SELECT COUNT(*) FROM lease_contract WHERE status='ACTIVE'"),
                count("SELECT COUNT(*) FROM bill WHERE status IN ('UNPAID','OVERDUE')"),revenue,cities,appointments);
    }

    private long count(String sql) { return jdbc.sql(sql).query(Long.class).single(); }
    private static String maskPhone(String value) {
        return value!=null && value.length()==11 ? value.substring(0,3)+"****"+value.substring(7) : "已隐藏";
    }
    private static OffsetDateTime utc(ResultSet rs,String name) throws SQLException {
        LocalDateTime value=rs.getObject(name,LocalDateTime.class);
        return value==null?null:value.atOffset(ZoneOffset.UTC);
    }
    private static final RowMapper<LeaseView> LEASE_VIEW=(rs,row)->new LeaseView(rs.getLong("id"),rs.getLong("room_id"),
            rs.getString("room_number"),rs.getString("apartment_name"),rs.getLong("user_id"),rs.getString("username"),
            rs.getObject("start_date",LocalDate.class),rs.getObject("end_date",LocalDate.class),rs.getBigDecimal("monthly_rent"),
            rs.getBigDecimal("deposit"),rs.getString("status"),utc(rs,"created_at"));
    private static final RowMapper<BillView> BILL_VIEW=(rs,row)->new BillView(rs.getLong("id"),rs.getLong("lease_id"),
            rs.getLong("user_id"),rs.getString("room_number"),rs.getString("apartment_name"),rs.getString("period"),
            rs.getObject("due_date",LocalDate.class),rs.getBigDecimal("amount"),rs.getString("status"),utc(rs,"paid_at"),utc(rs,"created_at"));
}
