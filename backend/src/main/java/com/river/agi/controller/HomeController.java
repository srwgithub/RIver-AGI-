package com.river.agi.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HomeController {
    
    @GetMapping("/")
    public Map<String, Object> home() {
        Map<String, Object> response = new HashMap<>();
        response.put("name", "RIver AGI - 对话式数据智能分析平台");
        response.put("version", "1.0.0");
        response.put("status", "running");
        
        Map<String, String> links = new HashMap<>();
        links.put("swagger", "/swagger-ui.html");
        links.put("h2-console", "/h2-console");
        links.put("actuator", "/actuator/health");
        links.put("login", "/api/v1/auth/login");
        response.put("links", links);
        
        return response;
    }
}
