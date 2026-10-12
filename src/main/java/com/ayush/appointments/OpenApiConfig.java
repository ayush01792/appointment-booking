package com.ayush.appointments;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
class OpenApiConfig {
  @Bean
  OpenAPI api() {
    return new OpenAPI()
        .info(new Info().title("Appointment Booking API").version("0.1.0"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearer", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
        .addSecurityItem(new SecurityRequirement().addList("bearer"));
  }
}
