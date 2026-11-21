package com.yuji.apartment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import java.nio.file.Path;

@Configuration
public class WebResourcesConfig implements WebMvcConfigurer {
    private final String uploads;
    public WebResourcesConfig(@Value("${app.uploads.directory:./runtime/uploads}") String uploads){this.uploads=uploads;}
    @Override public void addResourceHandlers(ResourceHandlerRegistry registry){
        String location=Path.of(uploads).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location.endsWith("/")?location:location+"/");
    }
}
