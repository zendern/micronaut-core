package io.micronaut.configuration.openapi.swagger;

import com.fasterxml.jackson.annotation.JsonView;
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
import io.swagger.v3.core.util.ParameterProcessor;
import io.swagger.v3.jaxrs2.OperationParser;
import io.swagger.v3.jaxrs2.Reader;
import io.swagger.v3.jaxrs2.ResolvedParameter;
import io.swagger.v3.jaxrs2.ext.OpenAPIExtension;
import io.swagger.v3.jaxrs2.ext.OpenAPIExtensions;
import io.swagger.v3.jaxrs2.util.ReaderUtils;
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
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;

import java.io.Writer;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Type;
import java.util.*;

public class SwaggerOpenAPIVisitor implements TypeElementVisitor<Object, Object> {

    private OpenAPI openAPI;
    private Set<io.swagger.v3.oas.models.tags.Tag> openApiTags = new LinkedHashSet<>();
    private Components components = new Components();
    private Paths paths = new Paths();
    private OperationClassData operationClassData = new OperationClassData();

    private static final String GET_METHOD = "get";
    private static final String POST_METHOD = "post";
    private static final String PUT_METHOD = "put";
    private static final String DELETE_METHOD = "delete";
    private static final String PATCH_METHOD = "patch";
    private static final String TRACE_METHOD = "trace";
    private static final String HEAD_METHOD = "head";
    private static final String OPTIONS_METHOD = "options";

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {

        if (!element.isInnerClass()) {
            operationClassData = new OperationClassData();
        }

        if (element.hasDeclaredAnnotation(OpenAPIDefinition.class)) {
            handleDefinition(element);
        }

        handleSecuritySchemes(getRepeatableAnnotations(element, SecuritySchemes.class, SecurityScheme.class));
        handleSecurityRequirements(getRepeatableAnnotations(element, SecurityRequirements.class, SecurityRequirement.class));
        handleTags(getRepeatableAnnotations(element, Tags.class, Tag.class));
        handleServers(getRepeatableAnnotations(element, Servers.class, Server.class));

        if (element.hasDeclaredAnnotation(ExternalDocumentation.class)) {
            handleExternalDocumentation(element.synthesizeDeclared(ExternalDocumentation.class));
        }

        Set<String> consumes = new HashSet<>();
        Set<String> produces = new HashSet<>();
        element.getValue(Consumes.class, String[].class).ifPresent(c -> consumes.addAll(Arrays.asList(c)));
        element.getValue(Produces.class, String[].class).ifPresent(p -> produces.addAll(Arrays.asList(p)));
        if (element.hasDeclaredAnnotation(Controller.class)) {
            Controller controller = element.synthesizeDeclared(Controller.class);
            operationClassData.setPath(controller.value());
            if (consumes.isEmpty()) {
                consumes.addAll(Arrays.asList(controller.consumes()));
            }
            if (produces.isEmpty()) {
                produces.addAll(Arrays.asList(controller.produces()));
            }
        }

        operationClassData.setConsumes(consumes.toArray(new String[0]));
        operationClassData.setProduces(produces.toArray(new String[0]));
        operationClassData.setApiResponses(getRepeatableAnnotations(element, ApiResponses.class, ApiResponse.class));
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
                String httpMethod = element.getAnnotationTypeByStereotype(HttpMethodMapping.class).get().getSimpleName().toUpperCase();

                String[] consumes = element.getValue(Consumes.class, String[].class)
                        .orElseGet(() -> {
                            return element.getValue(HttpMethodMapping.class, "consumes", String[].class).orElse(operationClassData.consumes);
                        });
                String[] produces = element.getValue(Produces.class, String[].class)
                        .orElseGet(() -> {
                            return element.getValue(HttpMethodMapping.class, "produces", String[].class).orElse(operationClassData.produces);
                        });

                javax.ws.rs.Consumes consumesAnnotation = synthesizeConsumes(consumes);
                javax.ws.rs.Produces producesAnnotation = synthesizeProduces(produces);

                Operation apiOperation = element.synthesizeDeclared(Operation.class);
                SecurityRequirement[] apiSecurity = getRepeatableAnnotations(element, SecurityRequirements.class, SecurityRequirement.class);
                Callback[] apiCallbacks = getRepeatableAnnotations(element, Callbacks.class, Callback.class);
                Server[] apiServers = getRepeatableAnnotations(element, Servers.class, Server.class);
                Tag[] apiTags = getRepeatableAnnotations(element, Tags.class, Tag.class);
                Parameter[] apiParameters = getRepeatableAnnotations(element, Parameters.class, Parameter.class);
                ApiResponse[] apiResponses = getRepeatableAnnotations(element, ApiResponses.class, ApiResponse.class);
                RequestBody apiRequestBody = element.synthesizeDeclared(RequestBody.class);
                ExternalDocumentation apiExternalDocumentation = element.synthesizeDeclared(ExternalDocumentation.class);
                JsonView jsonViewAnnotation = element.synthesizeDeclared(JsonView.class);
                if (apiOperation != null && apiOperation.ignoreJsonView()) {
                    jsonViewAnnotation = null;
                }

                // callbacks
                Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbacks = new LinkedHashMap<>();

                if (apiCallbacks != null) {
                    for (Callback methodCallback : apiCallbacks) {
                        Map<String, io.swagger.v3.oas.models.callbacks.Callback> currentCallbacks = getCallbacks(methodCallback, producesAnnotation, consumesAnnotation, jsonViewAnnotation);
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
                    requirementsObject.ifPresent(securityRequirements -> securityRequirements.stream()
                            .filter(r -> operation.getSecurity() == null || !operation.getSecurity().contains(r))
                            .forEach(operation::addSecurityItem));
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

                List<io.swagger.v3.oas.models.parameters.Parameter> globalParameters = null;

                // parameters
                if (globalParameters != null) {
                    for (io.swagger.v3.oas.models.parameters.Parameter globalParameter : globalParameters) {
                        operation.addParametersItem(globalParameter);
                    }
                }


                if (apiParameters != null) {
                    getParametersListFromAnnotation(
                            apiParameters,
                            consumesAnnotation,
                            jsonViewAnnotation).ifPresent(p -> p.forEach(operation::addParametersItem));
                }

                // RequestBody in Method
                if (apiRequestBody != null && operation.getRequestBody() == null){
                    OperationParser.getRequestBody(apiRequestBody, null, consumesAnnotation, components, jsonViewAnnotation).ifPresent(
                            operation::setRequestBody);
                }

                // operation id
                if (StringUtils.isEmpty(operation.getOperationId())) {
                    operation.setOperationId(getOperationId(element.getName()));
                }

                // classResponses
                if (operationClassData.apiResponses != null && operationClassData.apiResponses.length > 0) {
                    OperationParser.getApiResponses(
                            null,
                            null,
                            producesAnnotation,
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
                    setOperationObjectFromApiOperationAnnotation(operation, apiOperation, producesAnnotation, consumesAnnotation, jsonViewAnnotation);
                }

                // apiResponses
                if (apiResponses != null && apiResponses.length > 0) {
                    OperationParser.getApiResponses(
                            apiResponses,
                            null,
                            producesAnnotation,
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
                if (operationClassData.tags != null) {
                    operationClassData.tags.stream()
                            .filter(t -> operation.getTags() == null || (operation.getTags() != null && !operation.getTags().contains(t)))
                            .map(io.swagger.v3.oas.models.tags.Tag::getName)
                            .forEach(operation::addTagsItem);
                }

                // external docs of class if not defined in annotation of method or as field of Operation annotation
                if (operation.getExternalDocs() == null) {
                    Optional.ofNullable(operationClassData.externalDocumentation).ifPresent(operation::setExternalDocs);
                }


                // handle return type, add as response in case.
                ClassElement returnType = element.getReturnType();
                if (false) {
                    ResolvedSchema resolvedSchema = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(new Type() {
                        @Override
                        public String getTypeName() {
                            return returnType.getName();
                        }
                    }).resolveAsRef(true).jsonViewAnnotation(jsonViewAnnotation));
                    if (resolvedSchema.schema != null) {
                        Schema returnTypeSchema = resolvedSchema.schema;
                        Content content = new Content();
                        MediaType mediaType = new MediaType().schema(returnTypeSchema);
                        AnnotationsUtils.applyTypes(null, produces, content, mediaType);
                        if (operation.getResponses() == null) {
                            operation.responses(
                                    new io.swagger.v3.oas.models.responses.ApiResponses()._default(
                                            new io.swagger.v3.oas.models.responses.ApiResponse().description(Reader.DEFAULT_DESCRIPTION)
                                                    .content(content)
                                    )
                            );
                        }
                        if (operation.getResponses().getDefault() != null &&
                                StringUtils.isEmpty(operation.getResponses().getDefault().get$ref())) {
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
                /*

                if (operation.getResponses() == null || operation.getResponses().isEmpty()) {
                    Content content = new Content();
                    MediaType mediaType = new MediaType();
                    AnnotationsUtils.applyTypes(classProduces == null ? new String[0] : classProduces.value(),
                            methodProduces == null ? new String[0] : methodProduces.value(), content, mediaType);

                    ApiResponse apiResponseObject = new ApiResponse().description(DEFAULT_DESCRIPTION).content(content);
                    operation.setResponses(new ApiResponses()._default(apiResponseObject));
                }

*/
                PathItem pathItemObject;
                if (openAPI.getPaths() != null && openAPI.getPaths().get(path) != null) {
                    pathItemObject = openAPI.getPaths().get(path);
                } else {
                    pathItemObject = new PathItem();
                }


                setPathItemOperation(pathItemObject, httpMethod, operation);

                paths.addPathItem(path, pathItemObject);
                if (openAPI.getPaths() != null) {
                    this.paths.putAll(openAPI.getPaths());
                }

                openAPI.setPaths(this.paths);
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
            if (openAPI.getTags() != null) {
                tagsSet.addAll(openAPI.getTags());
            }
            tagsSet.addAll(openApiTags);
            openAPI.setTags(new ArrayList<>(tagsSet));
        }

        try {
            Writer writer = visitorContext.createDistFile("", "openapi.yaml");

            //after jackson-databind supports snakeyaml >= 1.2, this can be replaced with
            //io.swagger.v3.core.util.Yaml.pretty().writeValue(writer, openAPI);
            io.micronaut.configuration.openapi.swagger.Yaml.pretty().writeValue(writer, openAPI);
        } catch (Throwable e) {
            visitorContext.warn("Could not write the open api document", null);
        }
    }

    <T extends Annotation> T[] getRepeatableAnnotations(Element element, Class<? extends Annotation> repeatType, Class<T> singleType) {
        @SuppressWarnings("unchecked")
        T[] singleTypeArray = (T[])Array.newInstance(singleType, 0);
        if (element.hasDeclaredAnnotation(repeatType)) {
            return element.getValue(repeatType, (Class<T[]>)singleTypeArray.getClass()).orElse(singleTypeArray);
        }
        if (element.hasDeclaredAnnotation(singleType)) {
            T[] array = (T[])Array.newInstance(singleType, 1);
            array[0] = element.synthesizeDeclared(singleType);
            return array;
        }
        return singleTypeArray;
    }

    private Map<String, io.swagger.v3.oas.models.callbacks.Callback> getCallbacks(
            io.swagger.v3.oas.annotations.callbacks.Callback apiCallback,
            javax.ws.rs.Produces produces,
            javax.ws.rs.Consumes consumes,
            JsonView jsonViewAnnotation) {
        Map<String, io.swagger.v3.oas.models.callbacks.Callback> callbackMap = new HashMap<>();
        if (apiCallback == null) {
            return callbackMap;
        }
        io.swagger.v3.oas.models.callbacks.Callback callbackObject = new io.swagger.v3.oas.models.callbacks.Callback();
        PathItem pathItemObject = new PathItem();
        for (io.swagger.v3.oas.annotations.Operation callbackOperation : apiCallback.operation()) {
            io.swagger.v3.oas.models.Operation callbackNewOperation = new io.swagger.v3.oas.models.Operation();
            setOperationObjectFromApiOperationAnnotation(
                    callbackNewOperation,
                    callbackOperation,
                    produces,
                    consumes,
                    jsonViewAnnotation);
            setPathItemOperation(pathItemObject, callbackOperation.method(), callbackNewOperation);
        }

        callbackObject.addPathItem(apiCallback.callbackUrlExpression(), pathItemObject);
        callbackMap.put(apiCallback.name(), callbackObject);

        return callbackMap;
    }

    private void setPathItemOperation(PathItem pathItemObject, String method, io.swagger.v3.oas.models.Operation operation) {
        switch (method) {
            case POST_METHOD:
                pathItemObject.post(operation);
                break;
            case GET_METHOD:
                pathItemObject.get(operation);
                break;
            case DELETE_METHOD:
                pathItemObject.delete(operation);
                break;
            case PUT_METHOD:
                pathItemObject.put(operation);
                break;
            case PATCH_METHOD:
                pathItemObject.patch(operation);
                break;
            case TRACE_METHOD:
                pathItemObject.trace(operation);
                break;
            case HEAD_METHOD:
                pathItemObject.head(operation);
                break;
            case OPTIONS_METHOD:
                pathItemObject.options(operation);
                break;
            default:
                // Do nothing here
                break;
        }
    }

    private void setOperationObjectFromApiOperationAnnotation(
            io.swagger.v3.oas.models.Operation operation,
            io.swagger.v3.oas.annotations.Operation apiOperation,
            javax.ws.rs.Produces produces,
            javax.ws.rs.Consumes consumes,
            JsonView jsonViewAnnotation) {
        if (StringUtils.isNotEmpty(apiOperation.summary())) {
            operation.setSummary(apiOperation.summary());
        }
        if (StringUtils.isNotEmpty(apiOperation.description())) {
            operation.setDescription(apiOperation.description());
        }
        if (StringUtils.isNotEmpty(apiOperation.operationId())) {
            operation.setOperationId(getOperationId(apiOperation.operationId()));
        }
        if (apiOperation.deprecated()) {
            operation.setDeprecated(apiOperation.deprecated());
        }

        ReaderUtils.getStringListFromStringArray(apiOperation.tags()).ifPresent(tags -> {
            tags.stream()
                    .filter(t -> operation.getTags() == null || (operation.getTags() != null && !operation.getTags().contains(t)))
                    .forEach(operation::addTagsItem);
        });

        if (operation.getExternalDocs() == null) { // if not set in root annotation
            AnnotationsUtils.getExternalDocumentation(apiOperation.externalDocs()).ifPresent(operation::setExternalDocs);
        }

        OperationParser.getApiResponses(apiOperation.responses(), null, produces, components, jsonViewAnnotation).ifPresent(responses -> {
            if (operation.getResponses() == null) {
                operation.setResponses(responses);
            } else {
                responses.forEach(operation.getResponses()::addApiResponse);
            }
        });
        AnnotationsUtils.getServers(apiOperation.servers()).ifPresent(servers -> servers.forEach(operation::addServersItem));

        getParametersListFromAnnotation(
                apiOperation.parameters(),
                consumes,
                jsonViewAnnotation).ifPresent(p -> p.forEach(operation::addParametersItem));

        // security
        Optional<List<io.swagger.v3.oas.models.security.SecurityRequirement>> requirementsObject = SecurityParser.getSecurityRequirements(apiOperation.security());

        requirementsObject.ifPresent(securityRequirements -> securityRequirements.stream()
                .filter(r -> operation.getSecurity() == null || !operation.getSecurity().contains(r))
                .forEach(operation::addSecurityItem));

        // RequestBody in Operation
        if (apiOperation != null && apiOperation.requestBody() != null && operation.getRequestBody() == null) {
            OperationParser.getRequestBody(apiOperation.requestBody(), null, consumes, components, jsonViewAnnotation).ifPresent(
                    requestBodyObject -> operation.setRequestBody(requestBodyObject));
        }

        // Extensions in Operation
        if (apiOperation.extensions().length > 0) {
            Map<String, Object> extensions = AnnotationsUtils.getExtensions(apiOperation.extensions());
            for (String ext : extensions.keySet()) {
                operation.addExtension(ext, extensions.get(ext));
            }
        }
    }

    protected Optional<List<io.swagger.v3.oas.models.parameters.Parameter>> getParametersListFromAnnotation(Parameter[] parameters, javax.ws.rs.Consumes consumes, JsonView jsonViewAnnotation) {
        if (parameters == null) {
            return Optional.empty();
        }
        List<io.swagger.v3.oas.models.parameters.Parameter> parametersObject = new ArrayList<>();
        for (io.swagger.v3.oas.annotations.Parameter parameter : parameters) {

            ResolvedParameter resolvedParameter = getParameters(ParameterProcessor.getParameterType(parameter), Collections.singletonList(parameter), consumes, jsonViewAnnotation);
            parametersObject.addAll(resolvedParameter.parameters);
        }
        if (parametersObject.size() == 0) {
            return Optional.empty();
        }
        return Optional.of(parametersObject);
    }

    protected ResolvedParameter getParameters(Type type, List<Annotation> annotations, javax.ws.rs.Consumes consumes, JsonView jsonViewAnnotation) {
        final Iterator<OpenAPIExtension> chain = OpenAPIExtensions.chain();
        if (!chain.hasNext()) {
            return new ResolvedParameter();
        }
        Set<Type> typesToSkip = new HashSet<>();
        final OpenAPIExtension extension = chain.next();

        return extension.extractParameters(annotations, type, typesToSkip, components, null, consumes, true, jsonViewAnnotation, chain);
    }

    protected String getOperationId(String operationId) {
        boolean operationIdUsed = existOperationId(operationId);
        String operationIdToFind = null;
        int counter = 0;
        while (operationIdUsed) {
            operationIdToFind = String.format("%s_%d", operationId, ++counter);
            operationIdUsed = existOperationId(operationIdToFind);
        }
        if (operationIdToFind != null) {
            operationId = operationIdToFind;
        }
        return operationId;
    }

    private boolean existOperationId(String operationId) {
        if (openAPI == null) {
            return false;
        }
        if (openAPI.getPaths() == null || openAPI.getPaths().isEmpty()) {
            return false;
        }
        for (PathItem path : openAPI.getPaths().values()) {
            String pathOperationId = extractOperationIdFromPathItem(path);
            if (operationId.equalsIgnoreCase(pathOperationId)) {
                return true;
            }

        }
        return false;
    }

    private String extractOperationIdFromPathItem(PathItem path) {
        if (path.getGet() != null) {
            return path.getGet().getOperationId();
        } else if (path.getPost() != null) {
            return path.getPost().getOperationId();
        } else if (path.getPut() != null) {
            return path.getPut().getOperationId();
        } else if (path.getDelete() != null) {
            return path.getDelete().getOperationId();
        } else if (path.getOptions() != null) {
            return path.getOptions().getOperationId();
        } else if (path.getHead() != null) {
            return path.getHead().getOperationId();
        } else if (path.getPatch() != null) {
            return path.getPatch().getOperationId();
        }
        return "";
    }

    void handleDefinition(ClassElement element) {
        OpenAPIDefinition definition = element.synthesizeDeclared(OpenAPIDefinition.class);
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
                if (StringUtils.isNotEmpty(securitySchemePair.getKey())) {
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
        ApiResponse[] apiResponses = null;

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

        void setApiResponses(ApiResponse[] apiResponses) {
            this.apiResponses = apiResponses;
        }
    }

    javax.ws.rs.Consumes synthesizeConsumes(String[] consumes) {
        return new javax.ws.rs.Consumes() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return javax.ws.rs.Consumes.class;
            }

            @Override
            public String[] value() {
                return consumes;
            }
        };
    }

    javax.ws.rs.Produces synthesizeProduces(String[] produces) {
        return new javax.ws.rs.Produces() {
            @Override
            public Class<? extends Annotation> annotationType() {
                return javax.ws.rs.Produces.class;
            }

            @Override
            public String[] value() {
                return produces;
            }
        };
    }

}
