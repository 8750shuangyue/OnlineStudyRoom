package com.studyroom.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 启动时校验 JWT 密钥：长度必须至少 32 位，且不能使用公开的开发默认值。
 * 防止部署时忘记配置 JWT_SECRET，导致任何人都能伪造登录令牌。
 */
@Component
public class JwtSecretValidator {

    private static final Logger log = LoggerFactory.getLogger(JwtSecretValidator.class);
    private static final String DEV_DEFAULT = "dev-only-secret-change-before-deploy-0123456789";

    private final String secret;

    public JwtSecretValidator(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret;
    }

    @PostConstruct
    public void validate() {
        if (secret == null || secret.length() < 32 || DEV_DEFAULT.equals(secret)) {
            log.error("JWT_SECRET 未正确配置：长度必须至少 32 位，且不能使用默认开发密钥。");
            log.error("请在项目根目录的 .env 中设置 JWT_SECRET=<至少 32 位的随机字符串> 后重新启动。");
            throw new IllegalStateException("JWT_SECRET 未正确配置，已拒绝启动（安全保护）");
        }
    }
}
