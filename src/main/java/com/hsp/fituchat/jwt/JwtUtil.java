package com.hsp.fituchat.jwt;

import com.hsp.fituchat.error.BusinessException;
import com.hsp.fituchat.error.ErrorCode;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;

/**
 * JWT 검증 전용 유틸리티.
 * 채팅 서비스에서는 토큰 생성이 필요 없고 검증만 수행한다.
 * 메인 서비스와 같은 secret key를 공유하여 토큰을 검증한다.
 */
@Slf4j
@Component
public class JwtUtil {
    private final SecretKey secretKey;

    public JwtUtil(@Value("${jwt.secret}") String secret) {
        secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public Claims validateAndGetClaims(String token) throws Exception {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ErrorCode.JWT_EXPIRED);
        } catch (MalformedJwtException e) {
            throw new BusinessException(ErrorCode.INVALID_JWT);
        } catch (SignatureException e) {
            throw new BusinessException(ErrorCode.JWT_SIGNATURE_INVALID);
        } catch (UnsupportedJwtException e) {
            throw new BusinessException(ErrorCode.JWT_UNSUPPORTED);
        } catch (Exception e) {
            log.error("JWT parsing error", e);
            throw new BusinessException(ErrorCode.INTER_SERVER_ERROR);
        }
    }

    public Long getUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
        return claims.get("userId", Long.class);
    }

    public boolean isExpired(long tokenExpiryMillis) {
        return System.currentTimeMillis() > tokenExpiryMillis;
    }

    public long getExpiryMillis(Claims claims) {
        return claims.getExpiration().getTime();
    }
}
