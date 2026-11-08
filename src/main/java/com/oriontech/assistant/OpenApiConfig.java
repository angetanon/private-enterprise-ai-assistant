package com.oriontech.assistant;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Private Enterprise AI Assistant API")
                .description("Assistant RAG privé : modèles locaux Ollama, index pgvector")
                .version("0.0.1"));
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