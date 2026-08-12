package com.studyroom.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.studyroom.user.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final byte[] secret;
    private final long accessExpirationMs;
    private final long refreshExpirationMs;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-ms}") long accessExpirationMs,
                      @Value("${app.jwt.refresh-expiration-ms}") long refreshExpirationMs) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.accessExpirationMs = accessExpirationMs;
        this.refreshExpirationMs = refreshExpirationMs;
    }

    public String generateToken(User user) {
        return generate(user, TYPE_ACCESS, accessExpirationMs);
    }

    public String generateRefreshToken(User user) {
        return generate(user, TYPE_REFRESH, refreshExpirationMs);
    }

    private String generate(User user, String type, long expirationMs) {
        try {
            Date now = new Date();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(user.getUsername())
                    .claim("typ", type)
                    .issueTime(now)
                    .expirationTime(new Date(now.getTime() + expirationMs))
                    .build();
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
            jwt.sign(new MACSigner(secret));
            return jwt.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("生成 JWT 失败", e);
        }
    }

    public String extractUsername(String token) {
        return parseAndVerify(token).getSubject();
    }

    public boolean isTokenValid(String token, User user) {
        try {
            JWTClaimsSet claims = parseAndVerify(token);
            return TYPE_ACCESS.equals(claims.getStringClaim("typ"))
                    && claims.getSubject().equals(user.getUsername())
                    && claims.getExpirationTime().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRefreshTokenValid(String token, User user) {
        try {
            JWTClaimsSet claims = parseAndVerify(token);
            return TYPE_REFRESH.equals(claims.getStringClaim("typ"))
                    && claims.getSubject().equals(user.getUsername())
                    && claims.getExpirationTime().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    private JWTClaimsSet parseAndVerify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                throw new IllegalArgumentException("JWT 签名无效");
            }
            return jwt.getJWTClaimsSet();
        } catch (Exception e) {
            throw new IllegalArgumentException("JWT 无效", e);
        }
    }
}
