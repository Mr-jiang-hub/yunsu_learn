package com.yunshu.zhixue.common.security;

import com.yunshu.zhixue.common.api.ErrorCode;
import com.yunshu.zhixue.common.config.AppProperties;
import com.yunshu.zhixue.common.exception.BizException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

/**
 * JWT Access Token 签发与校验。
 *
 * <p>C/B 使用不同签名密钥与 audience；Refresh Token 是不透明随机串，
 * 由认证业务落库管理（哈希存储、轮换、撤销），不在本类实现。</p>
 */
@Component
public class JwtTokenProvider {

    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    private final AppProperties properties;
    private final SecretKey cKey;
    private final SecretKey adminKey;

    public JwtTokenProvider(AppProperties properties) {
        this.properties = properties;
        this.cKey = Keys.hmacShaKeyFor(properties.security().c().secret().getBytes(StandardCharsets.UTF_8));
        this.adminKey = Keys.hmacShaKeyFor(properties.security().admin().secret().getBytes(StandardCharsets.UTF_8));
    }

    /** 签发结果：token 与其过期时间同源，响应体 accessExpiresAt 不再二次推算 */
    public record AccessIssuance(String token, Instant expiresAt) {
    }

    public String createAccessToken(long subjectId, Audience audience) {
        return issueAccessToken(subjectId, audience).token();
    }

    public AccessIssuance issueAccessToken(long subjectId, Audience audience) {
        Instant now = Instant.now();
        Duration ttl = properties.security().chain(audience.claimValue()).accessTtl();
        Instant expiresAt = now.plus(ttl);
        String token = Jwts.builder()
                .subject(Long.toString(subjectId))
                .audience().add(audience.claimValue()).and()
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(keyOf(audience), Jwts.SIG.HS256)
                .compact();
        return new AccessIssuance(token, expiresAt);
    }

    /**
     * 校验并解析 access token。失败时抛出对应认证错误码：
     * 20002 过期 / 20003 无效 / 20007 端侧不匹配。
     */
    public AuthPrincipal parseAccessToken(String token, Audience expected) {
        try {
            Claims claims = parse(token, expected);
            return toPrincipal(claims, expected);
        } catch (ExpiredJwtException ex) {
            throw new BizException(ErrorCode.ACCESS_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            Audience other = expected == Audience.C ? Audience.ADMIN : Audience.C;
            if (canParse(token, other)) {
                throw new BizException(ErrorCode.TOKEN_AUDIENCE_MISMATCH);
            }
            throw new BizException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    private Claims parse(String token, Audience audience) {
        return Jwts.parser()
                .verifyWith(keyOf(audience))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private boolean canParse(String token, Audience audience) {
        try {
            parse(token, audience);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    private AuthPrincipal toPrincipal(Claims claims, Audience expected) {
        if (claims.getAudience() == null || !claims.getAudience().contains(expected.claimValue())) {
            throw new BizException(ErrorCode.TOKEN_AUDIENCE_MISMATCH);
        }
        if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new BizException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
        try {
            return new AuthPrincipal(Long.parseLong(claims.getSubject()), expected);
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    private SecretKey keyOf(Audience audience) {
        return audience == Audience.C ? cKey : adminKey;
    }
}
