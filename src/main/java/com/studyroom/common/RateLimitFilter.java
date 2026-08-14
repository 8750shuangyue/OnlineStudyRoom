package com.studyroom.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单接口限流（内存实现，固定窗口每分钟）：
 * - 认证接口：20 次/分钟（登录、注册等）
 * - AI 接口：15 次/分钟（防止异常流量白嫖 API 额度）
 * - 只读轮询接口：不计入限流，避免前端切页/轮询触发 429
 * - 其余接口：300 次/分钟
 * 已登录请求按用户维度计数（同一用户多设备共享额度），匿名请求按 IP 计数。
 * 客户端真实 IP 优先取 X-Real-IP / X-Forwarded-For（部署在 Nginx 后时生效）。
 * 该过滤器注册在 Spring Security 过滤链内（JwtAuthFilter 之后），
 * 因此能拿到已认证用户信息；通过 FilterRegistrationBean 禁用了 Servlet 容器自动注册，避免执行两次。
 */
@Component
public class RateLimitFilter implements Filter {

    private static final int AUTH_LIMIT = 20;
    private static final int AI_LIMIT = 15;
    private static final int DEFAULT_LIMIT = 300;

    private static final Set<String> POLLING_OK = Set.of(
            "/api/rooms/unread",
            "/api/notifications/unread-count",
            "/api/invites",
            "/api/friends/requests",
            "/api/feed");

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * 防止 Spring Boot 把限流过滤器自动注册成 Servlet 过滤器（那样会和安全链内执行重复）。
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String uri = req.getRequestURI();

        if (POLLING_OK.contains(uri)) {
            chain.doFilter(request, response);
            return;
        }

        boolean auth = uri.startsWith("/api/auth/");
        boolean ai = uri.startsWith("/api/ai");
        int limit = auth ? AUTH_LIMIT : ai ? AI_LIMIT : DEFAULT_LIMIT;
        String key = identity(req) + "|" + (auth ? "auth" : ai ? "ai" : "default");

        if (!windows.computeIfAbsent(key, k -> new Window()).allow(limit)) {
            res.setStatus(429);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"请求太频繁了，请稍后再试\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** 已登录用户按用户名计数（多设备共享额度），匿名请求按 IP 计数。 */
    private String identity(HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken)) {
            return "user:" + auth.getName();
        }
        return "ip:" + clientIp(req);
    }

    private String clientIp(HttpServletRequest req) {
        String realIp = req.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    static class Window {
        private long start = System.currentTimeMillis();
        private int count;

        synchronized boolean allow(int limit) {
            long now = System.currentTimeMillis();
            if (now - start >= 60_000) {
                start = now;
                count = 0;
            }
            count++;
            return count <= limit;
        }
    }
}
