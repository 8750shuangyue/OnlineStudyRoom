package com.studyroom.common;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单接口限流（内存版，固定窗口每分钟）：
 * 认证接口每 IP 每分钟 20 次，其余接口 300 次。
 * 配额按页面多接口并发 + 轮询预留，正常使用不会触发。
 */
@Component
public class RateLimitFilter implements Filter {

    private static final int AUTH_LIMIT = 20;
    private static final int DEFAULT_LIMIT = 300;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String uri = req.getRequestURI();
        boolean auth = uri.startsWith("/api/auth/");
        int limit = auth ? AUTH_LIMIT : DEFAULT_LIMIT;
        String key = req.getRemoteAddr() + "|" + (auth ? "auth" : "default");

        if (!windows.computeIfAbsent(key, k -> new Window()).allow(limit)) {
            res.setStatus(429);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(
                    "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        chain.doFilter(request, response);
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
