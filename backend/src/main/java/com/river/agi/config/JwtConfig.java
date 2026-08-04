package com.river.agi.config;

import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {
    private String secret;
    private long expiration;
    private boolean usingGeneratedSecret = false;
    
    @PostConstruct
    public void validate() {
        if (secret == null || secret.length() < 32) {
            log.warn("JWT_SECRET not set or too short (length: {}). Using generated random key for this session. Set JWT_SECRET env var for production stability.", 
                    secret == null ? 0 : secret.length());
            byte[] keyBytes = new byte[32];
            new SecureRandom().nextBytes(keyBytes);
            this.secret = new String(keyBytes, StandardCharsets.UTF_8);
            this.usingGeneratedSecret = true;
        } else {
            log.info("JWT secret validated successfully (length: {})", secret.length());
        }
    }
    
    @Bean
    public SecretKey jwtSecretKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
