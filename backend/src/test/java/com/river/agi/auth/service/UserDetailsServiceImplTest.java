package com.river.agi.auth.service;

import com.river.agi.auth.entity.User;
import com.river.agi.auth.mapper.RoleMapper;
import com.river.agi.auth.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleMapper roleMapper;

    private UserDetailsServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserDetailsServiceImpl(userMapper, roleMapper);
    }

    @Test
    @DisplayName("loadUserByUsername: returns UserDetails with mapped ROLE_ authorities")
    void loadUserByUsername_withRoles() {
        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        user.setPassword("hashed");
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectCodesByUserId(1L)).thenReturn(List.of("ADMIN", "EDITOR"));

        UserDetails details = service.loadUserByUsername("alice");

        assertNotNull(details);
        assertEquals("alice", details.getUsername());
        assertEquals("hashed", details.getPassword());
        assertTrue(details.isEnabled());
        assertEquals(2, details.getAuthorities().size());
        var it = details.getAuthorities().iterator();
        assertEquals("ROLE_ADMIN", it.next().getAuthority());
        assertEquals("ROLE_EDITOR", it.next().getAuthority());
    }

    @Test
    @DisplayName("loadUserByUsername: null roles fall back to USER role")
    void loadUserByUsername_nullRoles() {
        User user = new User();
        user.setId(2L);
        user.setUsername("bob");
        user.setPassword("hashed");
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectCodesByUserId(2L)).thenReturn(null);

        UserDetails details = service.loadUserByUsername("bob");

        assertEquals(1, details.getAuthorities().size());
        assertEquals("ROLE_USER", details.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("loadUserByUsername: empty roles fall back to USER role")
    void loadUserByUsername_emptyRoles() {
        User user = new User();
        user.setId(3L);
        user.setUsername("charlie");
        user.setPassword("hashed");
        user.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectCodesByUserId(3L)).thenReturn(Collections.emptyList());

        UserDetails details = service.loadUserByUsername("charlie");

        assertEquals(1, details.getAuthorities().size());
        assertEquals("ROLE_USER", details.getAuthorities().iterator().next().getAuthority());
    }

    @Test
    @DisplayName("loadUserByUsername: disabled account when status != 1")
    void loadUserByUsername_disabledAccount() {
        User user = new User();
        user.setId(4L);
        user.setUsername("dan");
        user.setPassword("hashed");
        user.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(roleMapper.selectCodesByUserId(4L)).thenReturn(List.of("USER"));

        UserDetails details = service.loadUserByUsername("dan");

        assertFalse(details.isEnabled());
    }

    @Test
    @DisplayName("loadUserByUsername: throws UsernameNotFoundException when user not found")
    void loadUserByUsername_notFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        UsernameNotFoundException ex = assertThrows(UsernameNotFoundException.class,
                () -> service.loadUserByUsername("ghost"));
        assertTrue(ex.getMessage().contains("ghost"));
        verify(roleMapper, never()).selectCodesByUserId(any());
    }
}
