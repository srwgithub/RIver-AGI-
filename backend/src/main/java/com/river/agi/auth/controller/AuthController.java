package com.river.agi.auth.controller;

import com.river.agi.auth.dto.LoginRequest;
import com.river.agi.auth.dto.LoginResponse;
import com.river.agi.auth.dto.RegisterRequest;
import com.river.agi.auth.dto.UserResponse;
import com.river.agi.auth.service.AuthService;
import com.river.agi.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and authorization APIs")
public class AuthController {
    
    private final AuthService authService;
    
    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticate user and get JWT token")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
    
    @PostMapping("/register")
    @Operation(summary = "Register", description = "Create a new user account")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }
    
    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Get information about the authenticated user")
    public ApiResponse<UserResponse> getCurrentUser(Authentication authentication) {
        return ApiResponse.ok(authService.getCurrentUser(authentication.getName()));
    }
}
