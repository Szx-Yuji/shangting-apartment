package com.yuji.apartment.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

/** Explicitly opt-in, synthetic learning data. Never runs with DEMO_ENABLED=false. */
@Component
@ConditionalOnProperty(name="app.demo.enabled",havingValue="true")
public class DemoDataInitializer implements ApplicationRunner {
    private final JdbcClient jdbc;
    private final PasswordEncoder encoder;
    public DemoDataInitializer(JdbcClient jdbc,PasswordEncoder encoder){this.jdbc=jdbc;this.encoder=encoder;}
    @Override @Transactional public void run(ApplicationArguments args){
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        if(jdbc.sql("SELECT COUNT(*) FROM admin_user").query(Long.class).single()==0){
            jdbc.sql("INSERT INTO admin_user(username,password_hash,enabled,created_at) VALUES(?,?,TRUE,?)")
                    .params("admin",encoder.encode("Admin123!2026"),now).update();
        }
        if(jdbc.sql("SELECT COUNT(*) FROM app_user").query(Long.class).single()==0){
            jdbc.sql("INSERT INTO app_user(username,password_hash,display_name,enabled,created_at) VALUES(?,?,?,TRUE,?)")
                    .params("tenant",encoder.encode("Tenant123!2026"),"演示租客",now).update();
        }
        if(jdbc.sql("SELECT COUNT(*) FROM apartment").query(Long.class).single()>0)return;
        String[] names={"南湖·悦居","净月·林间","运河·青寓","滨江·云舍","徐汇·和光","杨浦·里巷"};
        String[] cities={"长春","长春","杭州","杭州","上海","上海"};
        String[] addresses={"南湖生活圈 · 轻轨沿线","净月大学城 · 林荫街区","运河生活圈 · 地铁沿线","滨江生活圈 · 钱塘沿线","徐汇生活圈 · 社区花园","杨浦生活圈 · 大学路沿线"};
        for(int a=0;a<names.length;a++){
            long apartmentId=a+1;
            int base=a<2?1600:a<4?2800:3800;
            jdbc.sql("INSERT INTO apartment(id,name,city,monthly_rent,address,description,image_url) VALUES(?,?,?,?,?,?,?)")
                    .params(apartmentId,names[a],cities[a],base,addresses[a],"面向青年租客的整租社区，提供清晰房源、在线预约与租约管理。以下均为学习演示房源。","/images/apartment.svg").update();
            for(int r=0;r<3;r++){
                long roomId=a*3L+r+1;
                String title=List.of("向阳一居 · 静享独处","通透开间 · 城市日光","舒适两居 · 生活有序").get(r);
                jdbc.sql("INSERT INTO room(id,apartment_id,room_number,monthly_rent,published,title,area,bedrooms,orientation,description,image_url,tags) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)")
                        .params(roomId,apartmentId,"A"+(301+r),base+r*450,true,title,28+r*12,r==2?2:1,"南",
                                "采光良好，独立卫浴，基础家具齐全。社区位置与房源资料为合成演示数据，可在线选择时段预约看房。",
                                "/images/room-"+(r+1)+".svg","近地铁,独立卫浴,拎包入住").update();
                for(int d=1;d<=5;d++){
                    for(int hour:new int[]{10,14,17}){
                        LocalDateTime start=LocalDate.now(ZoneId.of("Asia/Shanghai")).plusDays(d).atTime(hour,0)
                                .atZone(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
                        jdbc.sql("INSERT INTO appointment_slot(room_id,start_time,end_time,capacity,reserved_count,created_at) VALUES(?,?,?,3,0,?)")
                                .params(roomId,start,start.plusMinutes(45),now).update();
                    }
                }
            }
        }
        Long userId=jdbc.sql("SELECT id FROM app_user WHERE username='tenant'").query(Long.class).optional().orElse(null);
        Long adminId=jdbc.sql("SELECT id FROM admin_user WHERE username='admin'").query(Long.class).optional().orElse(null);
        if(userId==null||adminId==null)return;
        for(int i=0;i<3;i++){
            long roomId=i+1;
            var slot=jdbc.sql("SELECT id,start_time FROM appointment_slot WHERE room_id=? ORDER BY id LIMIT 1")
                    .param(roomId).query((rs,n)->new Object[]{rs.getLong(1),rs.getObject(2,LocalDateTime.class)}).single();
            String status=List.of("PENDING","CONFIRMED","REJECTED").get(i);
            jdbc.sql("INSERT INTO appointment(id,room_id,slot_id,user_id,contact_name,contact_phone,appointment_time,status,created_at) VALUES(?,?,?,?,?,?,?,?,?)")
                    .params((long)i+1,roomId,slot[0],userId,"演示租客","13800000000",slot[1],status,now.minusHours(i+1)).update();
            if(!status.equals("REJECTED"))jdbc.sql("UPDATE appointment_slot SET reserved_count=1 WHERE id=?").param(slot[0]).update();
            if(i>0)jdbc.sql("INSERT INTO appointment_review_log(appointment_id,admin_id,from_status,to_status,created_at) VALUES(?,?,'PENDING',?,?)")
                    .params((long)i+1,adminId,status,now).update();
        }
        LocalDate today=LocalDate.now(ZoneOffset.UTC);
        jdbc.sql("INSERT INTO lease_contract(id,room_id,user_id,start_date,end_date,monthly_rent,deposit,status,created_at,updated_at) VALUES(1,18,?,?,?,?,?,'ACTIVE',?,?)")
                .params(userId,today.minusDays(10),today.plusMonths(6),4700,4700,now,now).update();
        jdbc.sql("INSERT INTO bill(lease_id,user_id,period,amount,due_date,status,created_at) VALUES(1,?,?,9400,?,'UNPAID',?)")
                .params(userId,today.minusDays(10).toString().substring(0,7),today.minusDays(10),now).update();
        jdbc.sql("INSERT INTO notification(user_id,title,content,is_read,created_at) VALUES(?,?,?,FALSE,?)")
                .params(userId,"欢迎来到尚庭公寓","演示账号已准备预约、租约和账单，可体验完整流程。演示支付不进行真实扣款。",now).update();
    }
}
