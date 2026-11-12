package com.oriontech.assistant;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                    .title("Private Enterprise AI Assistant API")
                    .description("Private RAG assistant: local Ollama models, pgvector index")
                    .version("0.0.1"))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP)
                                .scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    GroupedOpenApi assistantApi() {
        return GroupedOpenApi.builder()
                .group("assistant")
                .pathsToMatch("/api/*/chat/**", "/api/*/memory")
                .build();
    }

    @Bean
    GroupedOpenApi knowledgeApi() {
        return GroupedOpenApi.builder()
                .group("knowledge")
                .pathsToMatch("/api/documents/**")
                .build();
    }
}