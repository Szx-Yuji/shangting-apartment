package com.yuji.apartment.catalog;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageAndExportTest {
    @TempDir Path directory;
    @Test void normalizesUploadedImageAndNeverUsesClientFilename() throws Exception {
        var buffer=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB),"png",buffer);
        var file=new MockMultipartFile("file","../../outside.html","text/html",buffer.toByteArray());
        String url=new ImageStorage(directory.toString()).save(file);
        assertTrue(url.matches("/uploads/[0-9a-f-]{36}\\.png"));
        assertTrue(Files.exists(directory.resolve(url.substring("/uploads/".length()))));
        try(var files=Files.list(directory)){assertEquals(1,files.count());}
    }
    @Test void rejectsScriptsDisguisedAsImages(){
        var file=new MockMultipartFile("file","image.png","image/png","<script>alert(1)</script>".getBytes());
        assertThrows(ResponseStatusException.class,()->new ImageStorage(directory.toString()).save(file));
    }
    @Test void csvQuotesAndPreventsSpreadsheetFormulas(){
        assertEquals("\"'=1+1\"",AppointmentExportController.cell("=1+1"));
        assertEquals("\"'  +SUM(A1)\"",AppointmentExportController.cell("  +SUM(A1)"));
        assertEquals("\"a,\"\"b\"\"\"",AppointmentExportController.cell("a,\"b\""));
    }
}
