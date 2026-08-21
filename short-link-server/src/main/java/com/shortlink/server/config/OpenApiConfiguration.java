package com.shortlink.server.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置（/swagger-ui.html）。
 */
@Configuration
public class OpenApiConfiguration {

    @Bean
    public OpenAPI shortLinkOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Short Link API")
                .description("Java 高性能短链系统 REST API")
                .version("1.0.0"));
    }
}
