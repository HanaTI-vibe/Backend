package com.kopo.l2q.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("L2Q API Documentation")
                .version("1.0.0")
                .description("AI 기반 협업 학습 플랫폼 L2Q의 API 명세서입니다.");
        return new OpenAPI()
                .components(new Components())
                .info(info);
    }
} 