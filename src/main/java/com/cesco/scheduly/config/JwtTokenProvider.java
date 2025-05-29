package com.cesco.scheduly.config;

import io.jsonwebtoken.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String secretKey;

    private final long validityInMs = 3600_000; // 1 hour

    public String createToken(String studentId, Long userId) {
        Claims claims = Jwts.claims().setSubject(studentId);
        claims.put("user_id", userId);

        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityInMs);

        return Jwts.builder()
                .setClaims(claims)
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(SignatureAlgorithm.HS256, secretKey)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String getStudentIdFromToken(String token) {
        return Jwts.parser().setSigningKey(secretKey)
                .parseClaimsJws(token).getBody().getSubject();
    }
}
