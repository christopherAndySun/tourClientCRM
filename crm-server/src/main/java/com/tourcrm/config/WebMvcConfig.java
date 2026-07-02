package com.tourcrm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final Path uploadDir;

    public WebMvcConfig(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadDir.toUri().toString());
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof StringHttpMessageConverter stringConverter) {
                stringConverter.setDefaultCharset(StandardCharsets.UTF_8);
            }
            if (converter instanceof MappingJackson2HttpMessageConverter jsonConverter) {
                jsonConverter.setDefaultCharset(StandardCharsets.UTF_8);
                jsonConverter.setSupportedMediaTypes(List.of(
                        new MediaType("application", "json", StandardCharsets.UTF_8),
                        new MediaType("application", "*+json", StandardCharsets.UTF_8)
                ));
            }
        }
    }
}
