package com.river.agi.auth.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class LoginResponse {
    @JsonProperty("accessToken")
    private String accessToken;
    
    @JsonProperty("token")
    public String getToken() {
        return this.accessToken;
    }
    
    private String tokenType = "Bearer";
    private Long expiresIn;
    private UserInfo user;
    
    @Data
    public static class UserInfo {
        private Long id;
        private String username;
        private String email;
        private String realName;
        private String role;
    }
}
