package com.river.agi.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Username is required")
    private String username;
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;
    @Email(message = "Invalid email format")
    private String email;
    private String realName;

    /**
     * 隐私政策知情同意（合同 14.2.1）。
     * 注册即视为同意《隐私政策》，必须勾选。通过 @AssertTrue 强制校验。
     */
    @AssertTrue(message = "必须同意隐私政策后方可注册")
    private Boolean privacyConsent;
}
