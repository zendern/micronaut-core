package io.micronaut.configuration.openapi.swagger.groovy

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info

@OpenAPIDefinition(
        info = @Info(title = "test title", version = "1.2.3")
)
class MainDefinition {
}
