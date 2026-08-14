package com.studyroom.security;

import com.studyroom.common.RateLimitFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 生产环境开启安全响应头（CSP / HSTS / 禁 iframe 嵌入），
     * 默认关闭以免影响本地开发；生产 profile 里置为 true。
     */
    @Value("${app.security.headers-enabled:false}")
    private boolean securityHeadersEnabled;

    private static final String CSP = "default-src 'self'; script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
            + "font-src 'self' data:; connect-src 'self' ws: wss:; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtAuthFilter jwtAuthFilter,
                                                   RateLimitFilter rateLimitFilter) throws Exception {
        if (securityHeadersEnabled) {
            http.headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                    .frameOptions(frame -> frame.deny())
                    .httpStrictTransportSecurity(hsts -> hsts
                            .includeSubDomains(true)
                            .maxAgeInSeconds(31536000)));
        }
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 注册/登录、聊天接口、健康检查放行
                        .requestMatchers("/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/actuator/health", "/ws/**",
                                "/api/users/*/card").permitAll()
                        // 监控端点：metrics/prometheus 建议仅内网访问（nginx 默认只放行本机）
                        .requestMatchers("/actuator/prometheus", "/actuator/metrics", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 静态页面放行
                        .requestMatchers("/", "/index.html", "/static/**",
                                "/assets/**", "/sw.js", "/manifest.webmanifest",
                                "/icon-192.png", "/icon-512.png", "/maskable-512.png",
                                "/favicon.ico", "/error").permitAll()
                        // 其余接口（未来的房间、计时等）需要登录
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex.authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"error\":\"未登录或 token 无效\"}");
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitFilter, JwtAuthFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
