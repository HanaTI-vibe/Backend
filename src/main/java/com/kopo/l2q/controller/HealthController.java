package com.kopo.l2q.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Health Check", description = "서버 상태 확인 API")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "서버 헬스 체크", description = "서버가 정상적으로 동작하는지 확인합니다.")
    public String healthCheck() {
        return "OK";
    }
} 