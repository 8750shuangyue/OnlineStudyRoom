package com.studyroom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 前后端分离开发时的跨域配置。
 * 默认放行本地前端开发服务器（Vite 默认 5173，Next/其他常用 3000）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 允许的跨域来源（逗号分隔）。生产环境通常同源部署（Nginx 反代），
     * 一般不需要额外配置；如前端域名与后端不同，通过 CORS_ALLOWED_ORIGINS 覆盖。
     */
    @Value("${app.cors.allowed-origins:"
            + "http://localhost:5173,http://127.0.0.1:5173,"
            + "http://localhost:3000,http://127.0.0.1:3000}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            return;
        }
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.split(","))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
