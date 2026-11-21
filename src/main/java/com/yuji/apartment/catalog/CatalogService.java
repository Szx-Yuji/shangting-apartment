package com.yuji.apartment.catalog;

import com.yuji.apartment.cache.ApartmentListCache;
import com.yuji.apartment.monitor.TimedOperation;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static com.yuji.apartment.catalog.CatalogDtos.*;

@Service
public class CatalogService {
    private final JdbcClient jdbc;
    private final ApartmentListCache cache;
    public CatalogService(JdbcClient jdbc, ApartmentListCache cache) { this.jdbc=jdbc; this.cache=cache; }
    private static final String SELECT = """
            SELECT r.id,r.apartment_id,a.name apartment_name,a.city,a.address,r.room_number,
                   r.title,r.monthly_rent,r.area,r.bedrooms,r.orientation,r.image_url,r.tags,
                   r.published,r.description,
                   NOT EXISTS(SELECT 1 FROM lease_contract l WHERE l.room_id=r.id AND l.status='ACTIVE') available
            FROM room r JOIN apartment a ON a.id=r.apartment_id
            """;
    private static final RowMapper<Listing> LISTING = (rs,n) -> new Listing(
            rs.getLong("id"),rs.getLong("apartment_id"),rs.getString("apartment_name"),rs.getString("city"),
            rs.getString("address"),rs.getString("room_number"),rs.getString("title"),rs.getInt("monthly_rent"),
            rs.getBigDecimal("area"),rs.getInt("bedrooms"),rs.getString("orientation"),rs.getString("image_url"),
            tags(rs.getString("tags")),rs.getBoolean("published"),rs.getBoolean("available"),rs.getString("description"));
    private static final RowMapper<Apartment> APARTMENT = (rs,n) -> new Apartment(rs.getLong("id"),
            rs.getString("name"),rs.getString("city"),rs.getInt("monthly_rent"),rs.getString("address"),
            rs.getString("description"),rs.getString("image_url"),rs.getLong("room_count"));
    private static List<String> tags(String value) {
        return value==null || value.isBlank() ? List.of() : Arrays.stream(value.split(",")).filter(s->!s.isBlank()).toList();
    }
    static void paging(long page,long size) {
        if(page<1 || page>100000 || size<1 || size>100) throw bad("页码须为1到100000，每页条数须为1到100");
    }
    private static ResponseStatusException bad(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,detail); }
    private static ResponseStatusException missing(String detail) { return new ResponseStatusException(HttpStatus.NOT_FOUND,detail); }
    private static String image(String url,String fallback) {
        if(url==null || url.isBlank()) return fallback;
        if(!url.matches("/(?:images|uploads)/[A-Za-z0-9_./-]+") || url.contains("..")) throw bad("图片地址必须是站内图片");
        return url;
    }
    @TimedOperation
    @Transactional(readOnly=true)
    public Page<Listing> listings(boolean admin,Long apartmentId,String city,String q,Integer minRent,Integer maxRent,
                                  Boolean published,String sort,long page,long size) {
        paging(page,size);
        if(apartmentId!=null && apartmentId<1) throw bad("公寓ID必须大于0");
        if((minRent!=null && minRent<0)||(maxRent!=null && maxRent<0)||
                (minRent!=null && maxRent!=null && minRent>maxRent)) throw bad("租金区间不合法");
        if((q!=null && q.length()>80)||(city!=null && city.length()>50)) throw bad("查询条件过长");
        StringBuilder where=new StringBuilder(" WHERE 1=1"); List<Object> args=new ArrayList<>();
        if(!admin) where.append(" AND r.published=TRUE");
        else if(published!=null) {where.append(" AND r.published=?");args.add(published);}
        if(apartmentId!=null){where.append(" AND r.apartment_id=?");args.add(apartmentId);}
        if(city!=null&&!city.isBlank()){where.append(" AND a.city=?");args.add(city.strip());}
        if(q!=null&&!q.isBlank()){where.append(" AND (r.title LIKE ? OR a.name LIKE ? OR a.address LIKE ? OR r.room_number LIKE ?)");
            for(int i=0;i<4;i++)args.add("%"+q.strip()+"%");}
        if(minRent!=null){where.append(" AND r.monthly_rent>=?");args.add(minRent);}
        if(maxRent!=null){where.append(" AND r.monthly_rent<=?");args.add(maxRent);}
        String ordering=switch(sort==null?"newest":sort){
            case "newest","id" -> "r.id DESC";
            case "rentAsc","rent_asc" -> "r.monthly_rent ASC,r.id DESC";
            case "rentDesc","rent_desc" -> "r.monthly_rent DESC,r.id DESC";
            case "areaDesc" -> "r.area DESC,r.id DESC";
            default -> throw bad("排序条件不支持");
        };
        long total=jdbc.sql("SELECT COUNT(*) FROM room r JOIN apartment a ON a.id=r.apartment_id"+where)
                .params(args).query(Long.class).single();
        List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add((page-1)*size);
        List<Listing> records=jdbc.sql(SELECT+where+" ORDER BY "+ordering+" LIMIT ? OFFSET ?")
                .params(paged).query(LISTING).list();
        return new Page<>(records,total,page,size);
    }
    public Listing listing(Long id,boolean admin) {
        return jdbc.sql(SELECT+" WHERE r.id=?"+(admin?"":" AND r.published=TRUE"))
                .param(id).query(LISTING).optional().orElseThrow(()->missing("房源不存在或尚未上架"));
    }
    @Transactional(readOnly=true)
    public Page<Apartment> apartments(String city,long page,long size) {
        paging(page,size);String where=city==null||city.isBlank()?"":" WHERE a.city=?";
        List<Object> args=where.isEmpty()?List.of():List.of(city.strip());
        long total=jdbc.sql("SELECT COUNT(*) FROM apartment a"+where).params(args).query(Long.class).single();
        List<Object> paged=new ArrayList<>(args);paged.add(size);paged.add((page-1)*size);
        var rows=jdbc.sql("SELECT a.*,(SELECT COUNT(*) FROM room r WHERE r.apartment_id=a.id) room_count FROM apartment a"+
                where+" ORDER BY a.id DESC LIMIT ? OFFSET ?").params(paged).query(APARTMENT).list();
        return new Page<>(rows,total,page,size);
    }
    private Apartment apartment(Long id) {
        return jdbc.sql("SELECT a.*,(SELECT COUNT(*) FROM room r WHERE r.apartment_id=a.id) room_count FROM apartment a WHERE a.id=?")
                .param(id).query(APARTMENT).optional().orElseThrow(()->missing("公寓不存在"));
    }
    @Transactional
    public Apartment saveApartment(Long id,ApartmentInput in) {
        if(id==null){
            var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
            jdbc.sql("INSERT INTO apartment(name,city,monthly_rent,address,description,image_url) VALUES(?,?,?,?,?,?)")
                    .params(in.name().strip(),in.city().strip(),in.monthlyRent(),in.address().strip(),
                            in.description()==null?"":in.description(),image(in.imageUrl(),"/images/apartment.svg")).update(key,"id");
            id=Objects.requireNonNull(key.getKey()).longValue();
        }else{
            apartment(id);
            jdbc.sql("UPDATE apartment SET name=?,city=?,monthly_rent=?,address=?,description=?,image_url=? WHERE id=?")
                    .params(in.name().strip(),in.city().strip(),in.monthlyRent(),in.address().strip(),
                            in.description()==null?"":in.description(),image(in.imageUrl(),"/images/apartment.svg"),id).update();
        }
        cache.evictAllAfterCommit();return apartment(id);
    }
    @Transactional
    public void deleteApartment(Long id) {
        try{if(jdbc.sql("DELETE FROM apartment WHERE id=?").param(id).update()==0)throw missing("公寓不存在");}
        catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"公寓仍有关联房间，请先处理房间");}
        cache.evictAllAfterCommit();
    }
    @Transactional
    public Listing saveRoom(Long id,RoomInput in) {
        apartment(in.apartmentId());
        String joined=String.join(",",in.tags()==null?List.of():in.tags().stream().map(String::strip).distinct().toList());
        if(joined.length()>500)throw bad("房源标签过长");
        try{
            if(id==null){
                var key=new org.springframework.jdbc.support.GeneratedKeyHolder();
                jdbc.sql("INSERT INTO room(apartment_id,room_number,title,monthly_rent,area,bedrooms,orientation,description,image_url,tags,published) VALUES(?,?,?,?,?,?,?,?,?,?,?)")
                        .params(in.apartmentId(),in.roomNumber().strip(),in.title().strip(),in.monthlyRent(),in.area(),in.bedrooms(),
                                in.orientation(),in.description()==null?"":in.description(),image(in.imageUrl(),"/images/room-1.svg"),joined,in.published()).update(key,"id");
                id=Objects.requireNonNull(key.getKey()).longValue();
            }else{
                Long owner=jdbc.sql("SELECT apartment_id FROM room WHERE id=? FOR UPDATE").param(id).query(Long.class)
                        .optional().orElseThrow(()->missing("房间不存在"));
                if(!owner.equals(in.apartmentId()))throw bad("房间创建后不能迁移至其他公寓");
                jdbc.sql("UPDATE room SET room_number=?,title=?,monthly_rent=?,area=?,bedrooms=?,orientation=?,description=?,image_url=?,tags=?,published=? WHERE id=?")
                        .params(in.roomNumber().strip(),in.title().strip(),in.monthlyRent(),in.area(),in.bedrooms(),in.orientation(),
                                in.description()==null?"":in.description(),image(in.imageUrl(),"/images/room-1.svg"),joined,in.published(),id).update();
            }
        }catch(org.springframework.dao.DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"该公寓的房间号已存在");}
        return listing(id,true);
    }
    @Transactional
    public void deleteRoom(Long id) {
        try{if(jdbc.sql("DELETE FROM room WHERE id=?").param(id).update()==0)throw missing("房间不存在");}
        catch(DataIntegrityViolationException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"房间仍有关联记录，请改为下架");}
    }
}
