package com.kopo.l2q.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    
    private static final Logger logger = LoggerFactory.getLogger(HomeController.class);
    
    @GetMapping("/")
    public String home() {
        logger.info("=== 홈 페이지 접속 ===");
        return "Vibe Backend is running!";
    }
    
    @GetMapping("/health")
    public String health() {
        logger.info("=== 헬스 체크 ===");
        return "OK";
    }
} 