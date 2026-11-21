package com.yuji.apartment.catalog;

import com.yuji.apartment.dto.AppointmentQueryRequest;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.server.ResponseStatusException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.*;

@RestController
public class AppointmentExportController {
    private final JdbcClient jdbc;
    public AppointmentExportController(JdbcClient jdbc){this.jdbc=jdbc;}
    @GetMapping("/api/admin/appointments/export.csv")
    public ResponseEntity<StreamingResponseBody> export(@Valid @ModelAttribute AppointmentQueryRequest filter){
        if(filter.startTime()!=null&&filter.endTime()!=null&&!filter.startTime().isBefore(filter.endTime()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"开始时间必须早于结束时间");
        StringBuilder sql=new StringBuilder("""
                SELECT ap.id,a.name,r.room_number,ap.contact_name,ap.contact_phone,ap.appointment_time,ap.status
                FROM appointment ap JOIN room r ON r.id=ap.room_id JOIN apartment a ON a.id=r.apartment_id WHERE 1=1
                """);
        List<Object> params=new ArrayList<>();
        if(filter.status()!=null){sql.append(" AND ap.status=?");params.add(filter.status());}
        if(filter.roomId()!=null){sql.append(" AND ap.room_id=?");params.add(filter.roomId());}
        if(filter.startTime()!=null){sql.append(" AND ap.appointment_time>=?");params.add(filter.startTime().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());}
        if(filter.endTime()!=null){sql.append(" AND ap.appointment_time<?");params.add(filter.endTime().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());}
        sql.append(" ORDER BY ap.appointment_time,ap.id LIMIT 5000");
        String statement=sql.toString();List<Object> arguments=List.copyOf(params);
        StreamingResponseBody body=stream->{
            var writer=new OutputStreamWriter(stream,StandardCharsets.UTF_8);
            writer.write('\ufeff');writer.write("预约编号,公寓,房间,联系人,电话,预约时间UTC,状态\r\n");
            var rows=jdbc.sql(statement).params(arguments).query((rs,n)->{
                var values=new ArrayList<String>();
                for(int i=1;i<=7;i++)values.add(cell(rs.getString(i)));
                return String.join(",",values);
            }).list();
            for(String row:rows){writer.write(row);writer.write("\r\n");}
            writer.flush();
        };
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=appointments.csv")
                .header("X-Export-Limit","5000").body(body);
    }
    static String cell(String value){
        if(value==null)return "\"\"";
        // Prevent formula injection even when a value starts with whitespace/control characters.
        String leading=value.stripLeading();
        if(!leading.isEmpty()&&"=+-@".indexOf(leading.charAt(0))>=0)value="'"+value;
        return "\""+value.replace("\"","\"\"")+"\"";
    }
}
