package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.ParameterElement;

import javax.lang.model.element.VariableElement;

public class JavaParameterElement implements ParameterElement, AnnotationMetadataDelegate {

    private final AnnotationMetadata annotationMetadata;
    private final VariableElement element;
    private final JavaVisitorContext visitorContext;

    JavaParameterElement(VariableElement element, JavaVisitorContext visitorContext) {
        this.element = element;
        this.visitorContext = visitorContext;
        this.annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(element);
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public ClassElement getType() {
        return JavaClassElement.of(element.asType(), visitorContext);
    }

    @Override
    public String getName() {
        return element.getSimpleName().toString();
    }

    @Override
    public Object getNativeType() {
        return element;
    }
}
