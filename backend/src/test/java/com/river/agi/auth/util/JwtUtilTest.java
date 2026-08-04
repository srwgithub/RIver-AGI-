package com.river.agi.auth.util;

import com.river.agi.config.JwtConfig;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtUtilTest {

    private JwtUtil jwtUtil;
    private JwtConfig jwtConfig;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret("this-is-a-very-secret-test-key-32-chars!!");
        jwtConfig.setExpiration(3600000L);
        jwtUtil = new JwtUtil(jwtConfig);
    }

    private UserDetails mockUser(String username) {
        UserDetails details = mock(UserDetails.class);
        when(details.getUsername()).thenReturn(username);
        return details;
    }

    @Test
    @DisplayName("generateToken produces a non-empty compact token")
    void generateToken_returnsToken() {
        UserDetails user = mockUser("alice");
        String token = jwtUtil.generateToken(user);
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains("."));
    }

    @Test
    @DisplayName("extractUsername returns the subject from the token")
    void extractUsername() {
        UserDetails user = mockUser("alice");
        String token = jwtUtil.generateToken(user);
        assertEquals("alice", jwtUtil.extractUsername(token));
    }

    @Test
    @DisplayName("extractExpiration returns a future expiration date")
    void extractExpiration() {
        UserDetails user = mockUser("alice");
        String token = jwtUtil.generateToken(user);
        Date expiration = jwtUtil.extractExpiration(token);
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    @DisplayName("extractClaim resolves arbitrary claims")
    void extractClaim() {
        UserDetails user = mockUser("alice");
        String token = jwtUtil.generateToken(user);
        String subject = jwtUtil.extractClaim(token, Claims::getSubject);
        assertEquals("alice", subject);
    }

    @Test
    @DisplayName("isTokenExpired returns false for a fresh token")
    void isTokenExpired_false() {
        UserDetails user = mockUser("alice");
        String token = jwtUtil.generateToken(user);
        assertFalse(jwtUtil.isTokenExpired(token));
    }

    @Test
    @DisplayName("validateToken returns true for matching user")
    void validateToken_valid() {
        UserDetails user = mockUser("alice");
        String token = jwtUtil.generateToken(user);
        assertTrue(jwtUtil.validateToken(token, user));
    }

    @Test
    @DisplayName("validateToken returns false when username does not match")
    void validateToken_usernameMismatch() {
        UserDetails alice = mockUser("alice");
        UserDetails bob = mockUser("bob");
        String token = jwtUtil.generateToken(alice);
        assertFalse(jwtUtil.validateToken(token, bob));
    }

    @Test
    @DisplayName("isTokenExpired throws ExpiredJwtException for an already-expired token")
    void isTokenExpired_expiredThrows() {
        jwtConfig.setExpiration(-1000L); // already expired
        UserDetails user = mockUser("alice");
        String token = jwtUtil.generateToken(user);
        assertThrows(io.jsonwebtoken.ExpiredJwtException.class, () -> jwtUtil.isTokenExpired(token));
    }
}
