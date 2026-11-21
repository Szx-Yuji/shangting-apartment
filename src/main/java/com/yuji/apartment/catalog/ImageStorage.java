package com.yuji.apartment.catalog;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.UUID;

@Service
public class ImageStorage {
    private final Path directory;
    public ImageStorage(@Value("${app.uploads.directory:./runtime/uploads}") String directory){
        this.directory=Path.of(directory).toAbsolutePath().normalize();
    }
    public String save(MultipartFile file){
        if(file.isEmpty()||file.getSize()>5*1024*1024)throw bad("图片不能为空且不能超过5MB");
        try(var input=file.getInputStream();var stream=ImageIO.createImageInputStream(input)){
            var readers=ImageIO.getImageReaders(stream);
            if(!readers.hasNext())throw bad("仅支持PNG或JPEG图片");
            ImageReader reader=readers.next();
            try{
                reader.setInput(stream,true,true);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                if(!format.equals("png")&&!format.equals("jpeg")&&!format.equals("jpg"))throw bad("仅支持PNG或JPEG图片");
                if((long)reader.getWidth(0)*reader.getHeight(0)>25_000_000)throw bad("图片分辨率过大");
                var image=reader.read(0);
                Files.createDirectories(directory);
                String name=UUID.randomUUID()+".png";
                Path destination=directory.resolve(name).normalize();
                if(!destination.startsWith(directory))throw bad("图片路径不合法");
                try(var output=Files.newOutputStream(destination,StandardOpenOption.CREATE_NEW)){
                    if(!ImageIO.write(image,"png",output))throw new IOException("PNG codec unavailable");
                }
                return "/uploads/"+name;
            }finally{reader.dispose();}
        }catch(IOException exception){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"图片无法读取或保存，请重新选择图片");}
    }
    private static ResponseStatusException bad(String detail){return new ResponseStatusException(HttpStatus.BAD_REQUEST,detail);}
}
