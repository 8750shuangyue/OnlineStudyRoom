package com.studyroom.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档信息：访问 /swagger-ui.html 查看接口文档。
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI studyRoomOpenApi() {
        return new OpenAPI().info(new Info()
                .title("网页版自习室 API")
                .description("自习室前端交互接口：房间、专注、实时聊天、AI 助手、成就、统计等")
                .version("0.1.0"));
    }
}
