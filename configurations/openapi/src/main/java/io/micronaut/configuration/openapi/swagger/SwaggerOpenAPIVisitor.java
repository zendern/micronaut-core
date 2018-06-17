package io.micronaut.configuration.openapi.swagger;

import io.micronaut.inject.visitor.*;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.models.OpenAPI;

public class SwaggerOpenAPIVisitor implements TypeElementVisitor<Object, Object> {

    OpenAPI openAPI;

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (element.hasDeclaredAnnotation(OpenAPIDefinition.class)) {
            AnnotationsUtils.getInfo(element.getDeclaredAnnotation(OpenAPIDefinition.class).info()).ifPresent(info -> openAPI.setInfo(info));
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {

    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {

    }

    @Override
    public void start(VisitorContext visitorContext) {
        openAPI = new OpenAPI();
    }

    @Override
    public void finish(VisitorContext visitorContext) {
        Yaml.prettyPrint(openAPI);
    }
}
