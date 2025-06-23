package com.kopo.l2q.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/")
    public String home() {
        return "Vibe Backend is running!";
    }

    @GetMapping("/api/health")
    public String health() {
        return "OK";
    }
} 