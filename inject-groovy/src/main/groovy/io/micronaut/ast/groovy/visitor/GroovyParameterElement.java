package io.micronaut.ast.groovy.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.ParameterElement;
import org.codehaus.groovy.ast.Parameter;

public class GroovyParameterElement extends AbstractGroovyElement implements ParameterElement {

    private final Parameter parameter;

    /**
     * @param parameter          The {@link Parameter}
     * @param annotationMetadata The annotation metadata
     */
    GroovyParameterElement(Parameter parameter, AnnotationMetadata annotationMetadata) {
        super(annotationMetadata);
        this.parameter = parameter;
    }

    @Override
    public ClassElement getType() {
        return GroovyClassElement.of(parameter.getType());
    }

    @Override
    public String getName() {
        return parameter.getName();
    }

    @Override
    public Object getNativeType() {
        return parameter;
    }
}
