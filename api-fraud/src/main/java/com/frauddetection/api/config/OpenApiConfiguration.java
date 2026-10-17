package com.frauddetection.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI fraudApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Fraud Detection API")
                        .version("v1")
                        .description("API métier de la plateforme de détection de fraude : consultation des transactions "
                                + "analysées et des alertes générées par le moteur Spark Streaming (règles + modèle MLlib), "
                                + "gestion du cycle de vie des alertes (revue, confirmation, blocage, faux positif), "
                                + "suivi de la qualité de détection. Authentification : POST /api/v1/auth/login puis "
                                + "bouton Authorize avec le jeton obtenu."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
