package io.micronaut.configuration.openapi.swagger.groovy;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.tags.Tag;

@OpenAPIDefinition(
        info = @Info(title = "test title", version = "1.2.3"),
        tags = [ @Tag(name = "foo", description = "Foo"), @Tag(name = "bar") ],
        servers = [ @Server(url = "http://localhost:8080") ],
        security = [ @SecurityRequirement(name = "jwt") ],
        externalDocs = @ExternalDocumentation(url = "google.com", description = "google it")
)
@Tag(name = "direct")
@Tag(name = "direct2", description = "direct w/ description")
public class MainDefinition {
}
