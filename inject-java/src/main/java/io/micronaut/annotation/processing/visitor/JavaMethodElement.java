/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.annotation.processing.visitor;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.visitor.ClassElement;
import io.micronaut.inject.visitor.MethodElement;
import io.micronaut.inject.visitor.ParameterElement;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.TypeMirror;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A method element returning data from a {@link ExecutableElement}.
 *
 * @author James Kleeh
 * @since 1.0
 */
class JavaMethodElement extends AbstractJavaElement implements MethodElement {

    private final ExecutableElement executableElement;
    private final JavaVisitorContext visitorContext;

    /**
     * @param executableElement  The {@link ExecutableElement}
     * @param annotationMetadata The annotation metadata
     * @param visitorContext The visitor context
     */
    JavaMethodElement(
            ExecutableElement executableElement,
            AnnotationMetadata annotationMetadata,
            JavaVisitorContext visitorContext) {
        super(executableElement, annotationMetadata);
        this.executableElement = executableElement;
        this.visitorContext = visitorContext;
    }

    @Override
    public boolean isConstructor() {
        return executableElement.getKind() == ElementKind.CONSTRUCTOR;
    }

    @Override
    public ClassElement getReturnType() {
        return JavaClassElement.of(executableElement.getReturnType(), visitorContext);
    }

    @Override
    public List<ParameterElement> getParameters() {
        return executableElement.getParameters().stream().map(parameter -> {
            return new JavaParameterElement(parameter, visitorContext);
        }).collect(Collectors.toList());
    }
}
