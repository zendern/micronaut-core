package io.micronaut.configuration.openapi.swagger;

import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.visitor.*;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.annotations.servers.Servers;
import io.swagger.v3.oas.annotations.tags.Tags;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.apache.commons.lang3.StringUtils;

import java.io.Writer;
import java.util.*;

public class SwaggerOpenAPIVisitor implements TypeElementVisitor<Object, Object> {

    OpenAPI openAPI;
    private Set<io.swagger.v3.oas.models.tags.Tag> openApiTags;
    private Components components = new Components();
    private OperationClassData operationClassData = new OperationClassData();

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {

        if (!element.isInnerClass()) {
            operationClassData = new OperationClassData();
        }

        if (element.hasDeclaredAnnotation(OpenAPIDefinition.class)) {
            handleDefinition(element);
        }

        if (element.hasDeclaredAnnotation(SecuritySchemes.class)) {
            SecurityScheme[] securitySchemes = element.getValue(SecuritySchemes.class, "value", SecurityScheme[].class).orElse(new SecurityScheme[0]);
            handleSecuritySchemes(securitySchemes);
        }
        if (element.hasDeclaredAnnotation(SecurityScheme.class)) {
            handleSecuritySchemes(element.getDeclaredAnnotation(SecurityScheme.class));
        }

        if (element.hasDeclaredAnnotation(SecurityRequirements.class)) {
            SecurityRequirement[] requirements = element.getValue(SecurityRequirements.class, "value", SecurityRequirement[].class).orElse(new SecurityRequirement[0]);
            handleSecurityRequirements(requirements);
        }
        if (element.hasDeclaredAnnotation(SecurityRequirement.class)) {
            handleSecurityRequirements(element.getDeclaredAnnotation(SecurityRequirement.class));
        }

        if (element.hasDeclaredAnnotation(Tags.class)) {
            Tag[] tags = element.getValue(Tags.class, "value", Tag[].class).orElse(new Tag[0]);
            handleTags(tags);
        }
        if (element.hasDeclaredAnnotation(Tag.class)) {
            handleTags(element.getDeclaredAnnotation(Tag.class));
        }

        if (element.hasDeclaredAnnotation(Servers.class)) {
            Server[] servers = element.getValue(Server.class, "value", Server[].class).orElse(new Server[0]);
            handleServers(servers);
        }
        if (element.hasDeclaredAnnotation(Server.class)) {
            handleServers(element.getDeclaredAnnotation(Server.class));
        }

        if (element.hasDeclaredAnnotation(ExternalDocumentation.class)) {
            handleExternalDocumentation(element.getDeclaredAnnotation(ExternalDocumentation.class));
        }

        if (element.hasDeclaredAnnotation(Controller.class)) {
            element.getValue(Controller.class, String.class).ifPresent(operationClassData::setPath);
            element.getValue(Consumes.class, String[].class).ifPresent(operationClassData::setConsumes);
            element.getValue(Produces.class, String[].class).ifPresent(operationClassData::setProduces);
        }

    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.isConstructor()) {
            return;
        }

        if (element.hasStereotype(HttpMethodMapping.class)) {
            element.getValue(HttpMethodMapping.class, String.class).ifPresent(path -> {

                String[] consumes = element.getValue(Consumes.class, String[].class).orElse(operationClassData.consumes);
                String[] produces = element.getValue(Produces.class, String[].class).orElse(operationClassData.produces);

                if (element.hasDeclaredAnnotation(Operation.class)) {
                }

            });
        }

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

        if (!components.equals(new Components()) && openAPI.getComponents() == null) {
            openAPI.setComponents(components);
        }

        if (!openApiTags.isEmpty()) {
            Set<io.swagger.v3.oas.models.tags.Tag> tagsSet = new TreeSet<>(Comparator.comparing(io.swagger.v3.oas.models.tags.Tag::getName));
            tagsSet.addAll(openAPI.getTags());
            tagsSet.addAll(openApiTags);
            openAPI.setTags(new ArrayList<>(tagsSet));
        }

        try {
            Writer writer = visitorContext.createDistFile("", "openapi.yaml");
            Yaml.pretty().writeValue(writer, openAPI);
        } catch (Exception e) {
            visitorContext.warn("Could not write the open api document", null);
        }
    }

    void handleDefinition(ClassElement element) {
        OpenAPIDefinition definition = element.getDeclaredAnnotation(OpenAPIDefinition.class);
        AnnotationsUtils.getInfo(definition.info()).ifPresent((info) -> openAPI.setInfo(info));
        SecurityParser
                .getSecurityRequirements(definition.security())
                .ifPresent(s -> openAPI.setSecurity(s));

        AnnotationsUtils
                .getExternalDocumentation(definition.externalDocs())
                .ifPresent(docs -> openAPI.setExternalDocs(docs));

        // OpenApiDefinition tags
        AnnotationsUtils
                .getTags(definition.tags(), false)
                .ifPresent(tags -> openApiTags.addAll(tags));

        // OpenApiDefinition servers
        AnnotationsUtils.getServers(definition.servers()).ifPresent(servers -> openAPI.setServers(servers));

        // OpenApiDefinition extensions
        if (definition.extensions().length > 0) {
            openAPI.setExtensions(AnnotationsUtils.getExtensions(definition.extensions()));
        }
    }

    void handleSecuritySchemes(SecurityScheme... schemes) {
        for (SecurityScheme scheme : schemes) {
            SecurityParser.getSecurityScheme(scheme).ifPresent(securitySchemePair -> {
                Map<String, io.swagger.v3.oas.models.security.SecurityScheme> securitySchemeMap = new HashMap<>();
                if (StringUtils.isNotBlank(securitySchemePair.getKey())) {
                    securitySchemeMap.entrySet().add(securitySchemePair);
                    if (components.getSecuritySchemes() != null && components.getSecuritySchemes().size() != 0) {
                        components.getSecuritySchemes().putAll(securitySchemeMap);
                    } else {
                        components.setSecuritySchemes(securitySchemeMap);
                    }
                }
            });
        }
    }

    void handleSecurityRequirements(SecurityRequirement... requirements) {
       SecurityParser.getSecurityRequirements(requirements).ifPresent(operationClassData::addSecurityRequirements);
    }

    void handleTags(Tag... tags) {
        AnnotationsUtils
                .getTags(tags, false)
                .ifPresent(operationClassData::addTags);

        AnnotationsUtils
                .getTags(tags, true)
                .ifPresent(openApiTags::addAll);
    }

    void handleServers(Server... servers) {
        AnnotationsUtils.getServers(servers).ifPresent(operationClassData::addServers);
    }

    void handleExternalDocumentation(ExternalDocumentation externalDocumentation) {
        AnnotationsUtils.getExternalDocumentation(externalDocumentation).ifPresent(operationClassData::setExternalDocumentation);
    }

    static class OperationClassData {

        List<io.swagger.v3.oas.models.security.SecurityRequirement> securityRequirements = new ArrayList<>();
        Set<io.swagger.v3.oas.models.tags.Tag> tags = new HashSet<>();
        List<io.swagger.v3.oas.models.servers.Server> servers = new ArrayList<>();
        io.swagger.v3.oas.models.ExternalDocumentation externalDocumentation = null;
        String path = null;
        String[] consumes = null;
        String[] produces = null;

        OperationClassData() {

        }

        void addSecurityRequirements(List<io.swagger.v3.oas.models.security.SecurityRequirement> requirements) {
            this.securityRequirements.addAll(requirements);
        }

        void addTags(Set<io.swagger.v3.oas.models.tags.Tag> tags) {
            this.tags.addAll(tags);
        }

        void addServers(List<io.swagger.v3.oas.models.servers.Server> servers) {
            this.servers.addAll(servers);
        }

        void setExternalDocumentation(io.swagger.v3.oas.models.ExternalDocumentation externalDocumentation) {
            this.externalDocumentation = externalDocumentation;
        }

        void setPath(String path) {
            this.path = path;
        }

        void setConsumes(String[] consumes) {
            this.consumes = consumes;
        }

        void setProduces(String[] produces) {
            this.produces = produces;
        }
    }

    /*
     *  io.swagger.v3.oas.annotations.responses.ApiResponse[] classResponses = ReflectionUtils.getRepeatableAnnotationsArray(cls, io.swagger.v3.oas.annotations.responses.ApiResponse.class);

        List<io.swagger.v3.oas.annotations.security.SecurityScheme> apiSecurityScheme = ReflectionUtils.getRepeatableAnnotations(cls, io.swagger.v3.oas.annotations.security.SecurityScheme.class);
        List<io.swagger.v3.oas.annotations.security.SecurityRequirement> apiSecurityRequirements = ReflectionUtils.getRepeatableAnnotations(cls, io.swagger.v3.oas.annotations.security.SecurityRequirement.class);

        ExternalDocumentation apiExternalDocs = ReflectionUtils.getAnnotation(cls, ExternalDocumentation.class);
        io.swagger.v3.oas.annotations.tags.Tag[] apiTags = ReflectionUtils.getRepeatableAnnotationsArray(cls, io.swagger.v3.oas.annotations.tags.Tag.class);
        io.swagger.v3.oas.annotations.servers.Server[] apiServers = ReflectionUtils.getRepeatableAnnotationsArray(cls, io.swagger.v3.oas.annotations.servers.Server.class);

        javax.ws.rs.Consumes classConsumes = ReflectionUtils.getAnnotation(cls, javax.ws.rs.Consumes.class);
        javax.ws.rs.Produces classProduces = ReflectionUtils.getAnnotation(cls, javax.ws.rs.Produces.class);




        // class external docs
        Optional<io.swagger.v3.oas.models.ExternalDocumentation> classExternalDocumentation = AnnotationsUtils.getExternalDocumentation(apiExternalDocs);


        JavaType classType = TypeFactory.defaultInstance().constructType(cls);
        BeanDescription bd = Json.mapper().getSerializationConfig().introspect(classType);

        final List<Parameter> globalParameters = new ArrayList<>();

        // look for constructor-level annotated properties
        globalParameters.addAll(ReaderUtils.collectConstructorParameters(cls, components, classConsumes, null));

        // look for field-level annotated properties
        globalParameters.addAll(ReaderUtils.collectFieldParameters(cls, components, classConsumes, null));

        // iterate class methods
        Method methods[] = cls.getMethods();
        for (Method method : methods) {
            if (isOperationHidden(method)) {
                continue;
            }
            AnnotatedMethod annotatedMethod = bd.findMethod(method.getName(), method.getParameterTypes());
            javax.ws.rs.Produces methodProduces = ReflectionUtils.getAnnotation(method, javax.ws.rs.Produces.class);
            javax.ws.rs.Consumes methodConsumes = ReflectionUtils.getAnnotation(method, javax.ws.rs.Consumes.class);

            if (ReflectionUtils.isOverriddenMethod(method, cls)) {
                continue;
            }

            javax.ws.rs.Path methodPath = ReflectionUtils.getAnnotation(method, javax.ws.rs.Path.class);

            String operationPath = ReaderUtils.getPath(apiPath, methodPath, parentPath, isSubresource);

            // skip if path is the same as parent, e.g. for @ApplicationPath annotated application
            // extending resource config.
            if (ignoreOperationPath(operationPath, parentPath) && !isSubresource) {
                continue;
            }

            Map<String, String> regexMap = new LinkedHashMap<>();
            operationPath = PathUtils.parsePath(operationPath, regexMap);
            if (operationPath != null) {
                if (config != null && ReaderUtils.isIgnored(operationPath, config)) {
                    continue;
                }

                final Class<?> subResource = getSubResourceWithJaxRsSubresourceLocatorSpecs(method);

                String httpMethod = ReaderUtils.extractOperationMethod(method, OpenAPIExtensions.chain());
                httpMethod = (httpMethod == null && isSubresource) ? parentMethod : httpMethod;

                if (StringUtils.isBlank(httpMethod) && subResource == null) {
                    continue;
                } else if (StringUtils.isBlank(httpMethod) && subResource != null) {
                    Type returnType = method.getGenericReturnType();
                    if (shouldIgnoreClass(returnType.getTypeName()) && !returnType.equals(subResource)) {
                        continue;
                    }
                }

                io.swagger.v3.oas.annotations.Operation apiOperation = ReflectionUtils.getAnnotation(method, io.swagger.v3.oas.annotations.Operation.class);
                JsonView jsonViewAnnotation = ReflectionUtils.getAnnotation(method, JsonView.class);
                if (apiOperation != null && apiOperation.ignoreJsonView()) {
                    jsonViewAnnotation = null;
                }


                Operation operation = parseMethod(
                        method,
                        globalParameters,
                        methodProduces,
                        classProduces,
                        methodConsumes,
                        classConsumes,
                        classSecurityRequirements,
                        classExternalDocumentation,
                        classTags,
                        classServers,
                        isSubresource,
                        parentRequestBody,
                        parentResponses,
                        jsonViewAnnotation,
                        classResponses
                        );
                if (operation != null) {

                    List<Parameter> operationParameters = new ArrayList<>();
                    List<Parameter> formParameters = new ArrayList<>();
                    Annotation[][] paramAnnotations = ReflectionUtils.getParameterAnnotations(method);
                    if (annotatedMethod == null) { // annotatedMethod not null only when method with 0-2 parameters
                        Type[] genericParameterTypes = method.getGenericParameterTypes();
                        for (int i = 0; i < genericParameterTypes.length; i++) {
                            final Type type = TypeFactory.defaultInstance().constructType(genericParameterTypes[i], cls);
                            io.swagger.v3.oas.annotations.Parameter paramAnnotation = AnnotationsUtils.getAnnotation(io.swagger.v3.oas.annotations.Parameter.class, paramAnnotations[i]);
                            Type paramType = ParameterProcessor.getParameterType(paramAnnotation, true);
                            if (paramType == null) {
                                paramType = type;
                            } else {
                                if (!(paramType instanceof Class)) {
                                    paramType = type;
                                }
                            }
                            ResolvedParameter resolvedParameter = getParameters(paramType, Arrays.asList(paramAnnotations[i]), operation, classConsumes, methodConsumes, jsonViewAnnotation);
                            for (Parameter p : resolvedParameter.parameters) {
                                operationParameters.add(p);
                            }
                            if (resolvedParameter.requestBody != null) {
                                processRequestBody(
                                        resolvedParameter.requestBody,
                                        operation,
                                        methodConsumes,
                                        classConsumes,
                                        operationParameters,
                                        paramAnnotations[i],
                                        type,
                                        jsonViewAnnotation);
                            } else if (resolvedParameter.formParameter != null) {
                                // collect params to use together as request Body
                                formParameters.add(resolvedParameter.formParameter);
                            }
                        }
                    } else {
                        for (int i = 0; i < annotatedMethod.getParameterCount(); i++) {
                            AnnotatedParameter param = annotatedMethod.getParameter(i);
                            final Type type = TypeFactory.defaultInstance().constructType(param.getParameterType(), cls);
                            io.swagger.v3.oas.annotations.Parameter paramAnnotation = AnnotationsUtils.getAnnotation(io.swagger.v3.oas.annotations.Parameter.class, paramAnnotations[i]);
                            Type paramType = ParameterProcessor.getParameterType(paramAnnotation, true);
                            if (paramType == null) {
                                paramType = type;
                            } else {
                                if (!(paramType instanceof Class)) {
                                    paramType = type;
                                }
                            }
                            ResolvedParameter resolvedParameter = getParameters(paramType, Arrays.asList(paramAnnotations[i]), operation, classConsumes, methodConsumes, jsonViewAnnotation);
                            for (Parameter p : resolvedParameter.parameters) {
                                operationParameters.add(p);
                            }
                            if (resolvedParameter.requestBody != null) {
                                processRequestBody(
                                        resolvedParameter.requestBody,
                                        operation,
                                        methodConsumes,
                                        classConsumes,
                                        operationParameters,
                                        paramAnnotations[i],
                                        type,
                                        jsonViewAnnotation);
                            } else if (resolvedParameter.formParameter != null) {
                                // collect params to use together as request Body
                                formParameters.add(resolvedParameter.formParameter);
                            }
                        }
                    }
                    // if we have form parameters, need to merge them into single schema and use as request body..
                    if (formParameters.size() > 0) {
                        Schema mergedSchema = new ObjectSchema();
                        for (Parameter formParam: formParameters) {
                            mergedSchema.addProperties(formParam.getName(), formParam.getSchema());
                        }
                        Parameter merged = new Parameter().schema(mergedSchema);
                        processRequestBody(
                                merged,
                                operation,
                                methodConsumes,
                                classConsumes,
                                operationParameters,
                                new Annotation[0],
                                null,
                                jsonViewAnnotation);

                    }
                    if (operationParameters.size() > 0) {
                        for (Parameter operationParameter : operationParameters) {
                            operation.addParametersItem(operationParameter);
                        }
                    }

                    // if subresource, merge parent parameters
                    if (parentParameters != null) {
                        for (Parameter parentParameter : parentParameters) {
                            operation.addParametersItem(parentParameter);
                        }
                    }

                    if (subResource != null && !scannedResources.contains(subResource)) {
                        scannedResources.add(subResource);
                        read(subResource, operationPath, httpMethod, true, operation.getRequestBody(), operation.getResponses(), classTags, operation.getParameters(), scannedResources);
                        // remove the sub resource so that it can visit it later in another path
                        // but we have a room for optimization in the future to reuse the scanned result
                        // by caching the scanned resources in the reader instance to avoid actual scanning
                        // the the resources again
                        scannedResources.remove(subResource);
                        // don't proceed with root resource operation, as it's handled by subresource
                        continue;
                    }

                    final Iterator<OpenAPIExtension> chain = OpenAPIExtensions.chain();
                    if (chain.hasNext()) {
                        final OpenAPIExtension extension = chain.next();
                        extension.decorateOperation(operation, method, chain);
                    }

                    PathItem pathItemObject;
                    if (openAPI.getPaths() != null && openAPI.getPaths().get(operationPath) != null) {
                        pathItemObject = openAPI.getPaths().get(operationPath);
                    } else {
                        pathItemObject = new PathItem();
                    }

                    if (StringUtils.isBlank(httpMethod)) {
                        continue;
                    }
                    setPathItemOperation(pathItemObject, httpMethod, operation);

                    paths.addPathItem(operationPath, pathItemObject);
                    if (openAPI.getPaths() != null) {
                        this.paths.putAll(openAPI.getPaths());
                    }

                    openAPI.setPaths(this.paths);

                }
            }
        }

        // if no components object is defined in openApi instance passed by client, set openAPI.components to resolved components (if not empty)
        if (!isEmptyComponents(components) && openAPI.getComponents() == null) {
            openAPI.setComponents(components);
        }

        // add tags from class to definition tags
        AnnotationsUtils
                .getTags(apiTags, true).ifPresent(tags -> openApiTags.addAll(tags));

        if (!openApiTags.isEmpty()) {
            Set<Tag> tagsSet = new LinkedHashSet<>();
            if (openAPI.getTags() != null) {
                for (Tag tag : openAPI.getTags()) {
                    if (tagsSet.stream().noneMatch(t -> t.getName().equals(tag.getName()))) {
                        tagsSet.add(tag);
                    }
                }
            }
            for (Tag tag : openApiTags) {
                if (tagsSet.stream().noneMatch(t -> t.getName().equals(tag.getName()))) {
                    tagsSet.add(tag);
                }
            }
            openAPI.setTags(new ArrayList<>(tagsSet));
        }
     */
}
