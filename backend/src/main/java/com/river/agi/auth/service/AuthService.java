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
import com.river.agi.common.annotation.AuditOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsServiceImpl userDetailsService;
    
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        
        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));
        List<String> roles = userDetails.getAuthorities().stream()
                .map(auth -> auth.getAuthority().replaceFirst("^ROLE_", ""))
                .toList();
        String primaryRole = roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .findFirst()
                .orElse("USER");

        String token = jwtUtil.generateToken(userDetails);
        
        LoginResponse response = new LoginResponse();
        response.setAccessToken(token);
        response.setExpiresIn(86400000L);
        
        LoginResponse.UserInfo userInfo = new LoginResponse.UserInfo();
        userInfo.setId(user.getId());
        userInfo.setUsername(user.getUsername());
        userInfo.setEmail(user.getEmail());
        userInfo.setRealName(user.getRealName());
        userInfo.setRole(primaryRole);
        response.setUser(userInfo);
        
        return response;
    }
    
    @AuditOperation(action = "USER_REGISTER", resourceType = "USER", description = "Register new user account")
    public UserResponse register(RegisterRequest request) {
        // Check if username already exists
        if (userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) != null) {
            throw new BusinessException("Username already exists");
        }
        
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail());
        user.setRealName(request.getRealName());
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        
        userMapper.insert(user);
        return convertToUserResponse(user);
    }
    
    public UserResponse getCurrentUser(String username) {
        User user = userMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));
        return convertToUserResponse(user);
    }

    private UserResponse convertToUserResponse(User user) {
        if (user == null) {
            return null;
        }
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setTenantId(user.getTenantId());
        response.setUsername(user.getUsername());
        response.setEmail(user.getEmail());
        response.setPhone(user.getPhone());
        response.setRealName(user.getRealName());
        response.setRole(resolvePrimaryRole(user.getId()));
        response.setStatus(user.getStatus());
        response.setCreatedBy(user.getCreatedBy());
        response.setCreatedAt(user.getCreatedAt());
        response.setUpdatedAt(user.getUpdatedAt());
        return response;
    }

    private String resolvePrimaryRole(Long userId) {
        List<String> roles = roleMapper.selectCodesByUserId(userId);
        if (roles == null || roles.isEmpty()) {
            return "USER";
        }
        return roles.stream()
                .filter(role -> role != null && !role.isBlank())
                .findFirst()
                .orElse("USER");
    }
}
