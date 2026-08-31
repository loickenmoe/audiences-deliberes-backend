package com.afriland.audiences.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth"; // Can be any name

        return new OpenAPI()
                .info(new Info()
                        .title("Audiences Application API")
                        .version("v1.0")
                        .description("API documentation for the Audiences Web Application")
                        .termsOfService("http://swagger.io/terms/") // Replace with actual terms URL
                        .license(new License().name("Apache 2.0").url("http://springdoc.org"))) // Replace with actual license
                // Add security scheme component
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // Add security requirement to apply JWT globally to all endpoints
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName));
    }
}
