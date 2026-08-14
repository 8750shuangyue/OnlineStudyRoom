package com.studyroom.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

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

    /**
     * SPA 回退：前端路由（/join/xxx、/help 等）直接刷新时，
     * 未命中的路径统一回退到 index.html，交给前端路由渲染。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location)
                            throws IOException {
                        if (resourcePath.startsWith("api/")
                                || resourcePath.startsWith("ws/")
                                || resourcePath.startsWith("actuator/")) {
                            return null;
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        return new ClassPathResource("/static/index.html");
                    }
                });
    }
}
