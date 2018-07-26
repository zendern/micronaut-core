package io.micronaut.configuration.openapi.swagger;

import io.micronaut.core.util.StringUtils;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.HttpMethodMapping;
import io.micronaut.http.annotation.Produces;
import io.micronaut.inject.visitor.*;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.core.util.AnnotationsUtils;
import io.swagger.v3.core.util.ReflectionUtils;
import io.swagger.v3.core.util.Yaml;
import io.swagger.v3.jaxrs2.OperationParser;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.callbacks.Callback;
import io.swagger.v3.oas.annotations.callbacks.Callbacks;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
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

    <T extends Annotation> T[] getRepeatableAnnotations(Element element, Class<? extends Annotation> repeatType, Class<T> singleType) {
        @SuppressWarnings("unchecked")
        T[] singleTypeArray = (T[])Array.newInstance(singleType, 0);
        if (element.hasDeclaredAnnotation(repeatType)) {
            return element.getValue(repeatType, "value", (Class<T[]>)singleTypeArray.getClass()).orElse(singleTypeArray);
        }
        if (element.hasDeclaredAnnotation(singleType)) {
            T[] array = (T[])Array.newInstance(singleType, 1);
            array[0] = element.getDeclaredAnnotation(singleType);
            return array;
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (element.isConstructor()) {
            return;
        }

        if (element.hasStereotype(HttpMethodMapping.class)) {
            element.getValue(HttpMethodMapping.class, String.class).ifPresent(path -> {

                io.swagger.v3.oas.models.Operation operation = new io.swagger.v3.oas.models.Operation();

                path = StringUtils.prependUri(operationClassData.path, path);

                String[] consumes = element.getValue(Consumes.class, String[].class).orElse(operationClassData.consumes);
                String[] produces = element.getValue(Produces.class, String[].class).orElse(operationClassData.produces);

                Operation apiOperation = element.getDeclaredAnnotation(Operation.class);
                SecurityRequirement[] apiSecurity = getRepeatableAnnotations(element, SecurityRequirements.class, SecurityRequirement.class);
                Callback[] apiCallbacks = getRepeatableAnnotations(element, Callbacks.class, Callback.class);
                Server[] apiServers = getRepeatableAnnotations(element, Servers.class, Server.class);
                Tag[] apiTags = getRepeatableAnnotations(element, Tags.class, Tag.class);
                Parameter[] apiParameters = getRepeatableAnnotations(element, Parameters.class, Parameter.class);
                ApiResponse[] apiResponses = getRepeatableAnnotations(element, ApiResponses.class, ApiResponse.class);
                RequestBody apiRequestBody = element.getDeclaredAnnotation(RequestBody.class);
                ExternalDocumentation apiExternalDocumentation = element.getDeclaredAnnotation(ExternalDocumentation.class);


                // callbacks
                Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbacks = new LinkedHashMap<>();

                if (apiCallbacks != null) {
                    for (Callback methodCallback : apiCallbacks) {
                        Map<String, Callback> currentCallbacks = getCallbacks(methodCallback, methodProduces, classProduces, methodConsumes, classConsumes, jsonViewAnnotation);
                        callbacks.putAll(currentCallbacks);
                    }
                }
                if (callbacks.size() > 0) {
                    operation.setCallbacks(callbacks);
                }

                // security
                operationClassData.securityRequirements.forEach(operation::addSecurityItem);
                if (apiSecurity != null) {
                    Optional<List<io.swagger.v3.oas.models.security.SecurityRequirement>> requirementsObject = io.swagger.v3.jaxrs2.SecurityParser.getSecurityRequirements(apiSecurity);
                    if (requirementsObject.isPresent()) {
                        requirementsObject.get().stream()
                                .filter(r -> operation.getSecurity() == null || !operation.getSecurity().contains(r))
                                .forEach(operation::addSecurityItem);
                    }
                }

                operationClassData.servers.forEach(operation::addServersItem);

                if (apiServers != null) {
                    AnnotationsUtils.getServers(apiServers).ifPresent(servers -> servers.forEach(operation::addServersItem));
                }

                // external docs
                AnnotationsUtils.getExternalDocumentation(apiExternalDocumentation).ifPresent(operation::setExternalDocs);

                // method tags
                if (apiTags != null) {
                    Arrays.stream(apiTags)
                            .filter(t -> operation.getTags() == null || (operation.getTags() != null && !operation.getTags().contains(t.name())))
                            .map(t -> t.name())
                            .forEach(operation::addTagsItem);
                    AnnotationsUtils.getTags(apiTags, true).ifPresent(tags -> openApiTags.addAll(tags));
                }

                // parameters
                if (globalParameters != null) {
                    for (Parameter globalParameter : globalParameters) {
                        operation.addParametersItem(globalParameter);
                    }
                }
                if (apiParameters != null) {
                    getParametersListFromAnnotation(
                            apiParameters.toArray(new io.swagger.v3.oas.annotations.Parameter[apiParameters.size()]),
                            classConsumes,
                            methodConsumes,
                            operation,
                            jsonViewAnnotation).ifPresent(p -> p.forEach(operation::addParametersItem));
                }

                // RequestBody in Method
                if (apiRequestBody != null && operation.getRequestBody() == null){
                    OperationParser.getRequestBody(apiRequestBody, classConsumes, methodConsumes, components, jsonViewAnnotation).ifPresent(
                            operation::setRequestBody);
                }

                // operation id
                if (StringUtils.isEmpty(operation.getOperationId())) {
                    operation.setOperationId(getOperationId(method.getName()));
                }

                // classResponses
                if (classResponses != null && classResponses.length > 0) {
                    OperationParser.getApiResponses(
                            classResponses,
                            classProduces,
                            methodProduces,
                            components,
                            jsonViewAnnotation
                    ).ifPresent(responses -> {
                        if (operation.getResponses() == null) {
                            operation.setResponses(responses);
                        } else {
                            responses.forEach(operation.getResponses()::addApiResponse);
                        }
                    });
                }

                if (apiOperation != null) {
                    setOperationObjectFromApiOperationAnnotation(operation, apiOperation, methodProduces, classProduces, methodConsumes, classConsumes, jsonViewAnnotation);
                }

                // apiResponses
                if (apiResponses != null && apiResponses.size() > 0) {
                    OperationParser.getApiResponses(
                            apiResponses.toArray(new io.swagger.v3.oas.annotations.responses.ApiResponse[apiResponses.size()]),
                            classProduces,
                            methodProduces,
                            components,
                            jsonViewAnnotation
                    ).ifPresent(responses -> {
                        if (operation.getResponses() == null) {
                            operation.setResponses(responses);
                        } else {
                            responses.forEach(operation.getResponses()::addApiResponse);
                        }
                    });
                }

                // class tags after tags defined as field of @Operation
                if (classTags != null) {
                    classTags.stream()
                            .filter(t -> operation.getTags() == null || (operation.getTags() != null && !operation.getTags().contains(t)))
                            .forEach(operation::addTagsItem);
                }

                // external docs of class if not defined in annotation of method or as field of Operation annotation
                if (operation.getExternalDocs() == null) {
                    classExternalDocs.ifPresent(operation::setExternalDocs);
                }

                // if subresource, merge parent requestBody
                if (isSubresource && parentRequestBody != null) {
                    if (operation.getRequestBody() == null) {
                        operation.requestBody(parentRequestBody);
                    } else {
                        Content content = operation.getRequestBody().getContent();
                        if (content == null) {
                            content = parentRequestBody.getContent();
                            operation.getRequestBody().setContent(content);
                        } else if (parentRequestBody.getContent() != null){
                            for (String parentMediaType: parentRequestBody.getContent().keySet()) {
                                if (content.get(parentMediaType) == null) {
                                    content.addMediaType(parentMediaType, parentRequestBody.getContent().get(parentMediaType));
                                }
                            }
                        }
                    }
                }

                // handle return type, add as response in case.
                Type returnType = method.getGenericReturnType();
                final Class<?> subResource = getSubResourceWithJaxRsSubresourceLocatorSpecs(method);
                if (!shouldIgnoreClass(returnType.getTypeName()) && !returnType.equals(subResource)) {
                    ResolvedSchema resolvedSchema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(returnType).resolveAsRef(true).jsonViewAnnotation(jsonViewAnnotation));
                    if (resolvedSchema.schema != null) {
                        Schema returnTypeSchema = resolvedSchema.schema;
                        Content content = new Content();
                        MediaType mediaType = new MediaType().schema(returnTypeSchema);
                        AnnotationsUtils.applyTypes(classProduces == null ? new String[0] : classProduces.value(),
                                methodProduces == null ? new String[0] : methodProduces.value(), content, mediaType);
                        if (operation.getResponses() == null) {
                            operation.responses(
                                    new ApiResponses()._default(
                                            new ApiResponse().description(DEFAULT_DESCRIPTION)
                                                    .content(content)
                                    )
                            );
                        }
                        if (operation.getResponses().getDefault() != null &&
                                StringUtils.isBlank(operation.getResponses().getDefault().get$ref())) {
                            if (operation.getResponses().getDefault().getContent() == null) {
                                operation.getResponses().getDefault().content(content);
                            } else {
                                for (String key : operation.getResponses().getDefault().getContent().keySet()) {
                                    if (operation.getResponses().getDefault().getContent().get(key).getSchema() == null) {
                                        operation.getResponses().getDefault().getContent().get(key).setSchema(returnTypeSchema);
                                    }
                                }
                            }
                        }
                        Map<String, Schema> schemaMap = resolvedSchema.referencedSchemas;
                        if (schemaMap != null) {
                            schemaMap.forEach((key, schema) -> components.addSchemas(key, schema));
                        }

                    }
                }
                if (operation.getResponses() == null || operation.getResponses().isEmpty()) {
                    Content content = new Content();
                    MediaType mediaType = new MediaType();
                    AnnotationsUtils.applyTypes(classProduces == null ? new String[0] : classProduces.value(),
                            methodProduces == null ? new String[0] : methodProduces.value(), content, mediaType);

                    ApiResponse apiResponseObject = new ApiResponse().description(DEFAULT_DESCRIPTION).content(content);
                    operation.setResponses(new ApiResponses()._default(apiResponseObject));
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
