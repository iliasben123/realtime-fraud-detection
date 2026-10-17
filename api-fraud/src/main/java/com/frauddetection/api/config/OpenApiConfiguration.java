package com.frauddetection.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfiguration {

    @Bean
    OpenAPI fraudApi() {
        return new OpenAPI().info(new Info()
                .title("Fraud Detection API")
                .version("v1")
                .description("API métier de la plateforme de détection de fraude : consultation des transactions "
                        + "analysées et des alertes générées par le moteur Spark Streaming, gestion du cycle de vie "
                        + "des alertes (revue, confirmation, blocage, faux positif)."));
    }
}
