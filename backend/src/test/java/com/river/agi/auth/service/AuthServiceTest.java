package com.river.agi.auth.service;

import com.river.agi.auth.dto.LoginRequest;
import com.river.agi.auth.dto.LoginResponse;
import com.river.agi.auth.dto.RegisterRequest;
import com.river.agi.auth.dto.UserResponse;
import com.river.agi.auth.entity.User;
import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.auth.mapper.UserMapper;
import com.river.agi.auth.util.JwtUtil;
import com.river.agi.common.BusinessException;
import com.river.agi.privacy.service.PrivacyService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserDetailsServiceImpl userDetailsService;
    @Mock
    private PrivacyService privacyService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userMapper, roleMapper, passwordEncoder, jwtUtil,
                authenticationManager, userDetailsService, privacyService);
    }

    // ===== login =====

    @Test
    @DisplayName("login: returns token and user info for valid credentials")
    void login_success() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("secret");

        UserDetails userDetails = mockUserDetails("alice", "ROLE_ADMIN");
        when(userDetailsService.loadUserByUsername("alice")).thenReturn(userDetails);
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));
        when(jwtUtil.generateToken(userDetails)).thenReturn("mock-token");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("mock-token", response.getAccessToken());
        assertEquals(86400000L, response.getExpiresIn());
        assertEquals("Bearer", response.getTokenType());
        LoginResponse.UserInfo info = response.getUser();
        assertNotNull(info);
        assertEquals(1L, info.getId());
        assertEquals("alice", info.getUsername());
        assertEquals("alice@example.com", info.getEmail());
        assertEquals("Alice", info.getRealName());
        assertEquals("ADMIN", info.getRole());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    @DisplayName("login: strips ROLE_ prefix and falls back to USER for plain authority")
    void login_roleUserFallback() {
        LoginRequest request = new LoginRequest();
        request.setUsername("bob");
        request.setPassword("secret");

        UserDetails userDetails = mockUserDetails("bob", "ROLE_USER");
        when(userDetailsService.loadUserByUsername("bob")).thenReturn(userDetails);
        when(userMapper.selectOne(any())).thenReturn(buildUser(2L, "bob"));
        when(jwtUtil.generateToken(userDetails)).thenReturn("tok");

        LoginResponse response = authService.login(request);

        assertEquals("USER", response.getUser().getRole());
    }

    @Test
    @DisplayName("login: empty authorities fall back to USER role")
    void login_emptyAuthorities() {
        LoginRequest request = new LoginRequest();
        request.setUsername("charlie");
        request.setPassword("secret");

        UserDetails userDetails = mock(UserDetails.class);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());
        when(userDetailsService.loadUserByUsername("charlie")).thenReturn(userDetails);
        when(userMapper.selectOne(any())).thenReturn(buildUser(3L, "charlie"));
        when(jwtUtil.generateToken(userDetails)).thenReturn("tok");

        LoginResponse response = authService.login(request);

        assertEquals("USER", response.getUser().getRole());
    }

    @Test
    @DisplayName("login: authority without ROLE_ prefix is kept as-is")
    void login_authorityWithoutPrefix() {
        LoginRequest request = new LoginRequest();
        request.setUsername("dan");
        request.setPassword("secret");

        UserDetails userDetails = mockUserDetails("dan", "MANAGER");
        when(userDetailsService.loadUserByUsername("dan")).thenReturn(userDetails);
        when(userMapper.selectOne(any())).thenReturn(buildUser(4L, "dan"));
        when(jwtUtil.generateToken(userDetails)).thenReturn("tok");

        LoginResponse response = authService.login(request);

        assertEquals("MANAGER", response.getUser().getRole());
    }

    @Test
    @DisplayName("login: bad credentials propagate BadCredentialsException")
    void login_badCredentials() {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword("wrong");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(userDetailsService, never()).loadUserByUsername(any());
    }

    // ===== register =====

    @Test
    @DisplayName("register: creates user when username is free")
    void register_success() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("password");
        request.setEmail("alice@example.com");
        request.setRealName("Alice");
        request.setPrivacyConsent(true);

        when(userMapper.selectOne(any())).thenReturn(null);
        when(passwordEncoder.encode("password")).thenReturn("encoded-pw");
        when(roleMapper.selectCodesByUserId(any())).thenReturn(List.of("ADMIN"));

        UserResponse response = authService.register(request, mockHttpRequest());

        assertNotNull(response);
        assertEquals("alice", response.getUsername());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("Alice", response.getRealName());
        assertEquals(1, response.getStatus());
        assertEquals("ADMIN", response.getRole());
        assertNotNull(response.getCreatedAt());
        assertNotNull(response.getUpdatedAt());

        org.mockito.ArgumentCaptor<User> captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("encoded-pw", inserted.getPassword());
        assertEquals("alice", inserted.getUsername());
    }

    @Test
    @DisplayName("register: throws BusinessException when username already exists")
    void register_usernameExists() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("password");
        request.setPrivacyConsent(true);

        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request, mockHttpRequest()));
        assertTrue(ex.getMessage().contains("Username already exists"));
        verify(userMapper, never()).insert(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("register: rejects registration without privacy consent")
    void register_withoutConsent() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("alice");
        request.setPassword("password");
        request.setPrivacyConsent(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> authService.register(request, mockHttpRequest()));
        assertTrue(ex.getMessage().contains("隐私政策"));
        verify(userMapper, never()).insert(any());
    }

    // ===== getCurrentUser =====

    @Test
    @DisplayName("getCurrentUser: returns user response when user found")
    void getCurrentUser_found() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));
        when(roleMapper.selectCodesByUserId(1L)).thenReturn(List.of("ADMIN"));

        UserResponse response = authService.getCurrentUser("alice");

        assertNotNull(response);
        assertEquals(1L, response.getId());
        assertEquals("alice", response.getUsername());
        assertEquals("alice@example.com", response.getEmail());
        assertEquals("Alice", response.getRealName());
        assertEquals("ADMIN", response.getRole());
        assertEquals(1, response.getStatus());
        assertEquals(7L, response.getTenantId());
        assertEquals("555-1234", response.getPhone());
    }

    @Test
    @DisplayName("getCurrentUser: returns null when user not found")
    void getCurrentUser_notFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        UserResponse response = authService.getCurrentUser("nobody");

        assertNull(response);
        verify(roleMapper, never()).selectCodesByUserId(any());
    }

    @Test
    @DisplayName("resolvePrimaryRole: null roles fall back to USER")
    void getCurrentUser_nullRolesFallback() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));
        when(roleMapper.selectCodesByUserId(1L)).thenReturn(null);

        UserResponse response = authService.getCurrentUser("alice");

        assertEquals("USER", response.getRole());
    }

    @Test
    @DisplayName("resolvePrimaryRole: empty roles fall back to USER")
    void getCurrentUser_emptyRolesFallback() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));
        when(roleMapper.selectCodesByUserId(1L)).thenReturn(Collections.emptyList());

        UserResponse response = authService.getCurrentUser("alice");

        assertEquals("USER", response.getRole());
    }

    @Test
    @DisplayName("resolvePrimaryRole: blank-only roles fall back to USER")
    void getCurrentUser_blankRolesFallback() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));
        when(roleMapper.selectCodesByUserId(1L)).thenReturn(List.of("  ", ""));

        UserResponse response = authService.getCurrentUser("alice");

        assertEquals("USER", response.getRole());
    }

    @Test
    @DisplayName("resolvePrimaryRole: returns first non-blank role")
    void getCurrentUser_firstNonBlankRole() {
        when(userMapper.selectOne(any())).thenReturn(buildUser(1L, "alice"));
        when(roleMapper.selectCodesByUserId(1L)).thenReturn(List.of("  ", "EDITOR", "ADMIN"));

        UserResponse response = authService.getCurrentUser("alice");

        assertEquals("EDITOR", response.getRole());
    }

    // ===== helpers =====

    private HttpServletRequest mockHttpRequest() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        lenient().when(request.getHeader(anyString())).thenReturn(null);
        lenient().when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        return request;
    }

    private UserDetails mockUserDetails(String username, String... authorityNames) {
        UserDetails details = mock(UserDetails.class);
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(authorityNames)
                .map(SimpleGrantedAuthority::new)
                .collect(java.util.stream.Collectors.toList());
        doReturn(authorities).when(details).getAuthorities();
        return details;
    }

    private User buildUser(Long id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword("hashed");
        user.setEmail("alice@example.com");
        user.setRealName("Alice");
        user.setPhone("555-1234");
        user.setStatus(1);
        user.setTenantId(7L);
        user.setCreatedBy(1L);
        user.setCreatedAt(java.time.LocalDateTime.now());
        user.setUpdatedAt(java.time.LocalDateTime.now());
        return user;
    }
}
