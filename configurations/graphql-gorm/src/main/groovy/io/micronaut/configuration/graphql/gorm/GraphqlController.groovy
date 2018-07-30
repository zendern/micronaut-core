package io.micronaut.configuration.graphql.gorm

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import io.micronaut.configuration.graphql.gorm.context.GraphQLContextBuilder
import io.micronaut.core.io.ResourceLoader
import io.micronaut.core.io.ResourceResolver
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.server.types.files.StreamedFile

import javax.annotation.Nullable
import javax.inject.Inject

@Controller("/graphql")
class GraphqlController {

    @Inject GraphqlConfiguration configuration
    @Inject ObjectMapper objectMapper
    @Inject GraphQLContextBuilder graphQLContextBuilder
    @Inject GraphQL graphQL
    @Inject ResourceResolver resourceResolver

    private static final String graphiql = "classpath:graphiql.html"
    private static URL cachedBrowser = null

    private Map parseJson(String json) {
        def typeRef = new TypeReference<HashMap<String,Object>>() {}
        objectMapper.readValue(json, typeRef)
    }

    private HttpResponse<GraphQLResponse> executeRequest(GraphQLRequest graphQLRequest, HttpRequest request) {
        Object context = graphQLContextBuilder.buildContext(request)

        ExecutionResult executionResult = graphQL.execute(ExecutionInput.newExecutionInput()
                .query(graphQLRequest.query)
                .operationName(graphQLRequest.operationName)
                .context(context)
                .root(context)
                .variables(graphQLRequest.variables)
                .build())

        GraphQLResponse result = new GraphQLResponse()

        if (executionResult.errors.size() > 0) {
            result.errors = executionResult.errors
        }
        result.data = executionResult.data

        HttpResponse.ok(result)
    }

    @Get("/")
    HttpResponse get(String query, @Nullable String operationName, @Nullable String variables, HttpRequest request) {
        if (!configuration.enabled) {
            return HttpResponse.notFound()
        }
        Map variableMap = parseJson(variables)
        executeRequest(new GraphQLRequest(query, operationName, variableMap), request)
    }

    @Post("/")
    HttpResponse post(@Header String contentType, @Body String body, HttpRequest request) {
        if (!configuration.enabled) {
            return HttpResponse.notFound()
        }
        if (contentType == MediaType.APPLICATION_JSON) {
            Map data = parseJson(body)
            if (data.containsKey('query')) {
                return executeRequest(new GraphQLRequest(data), request)
            }
        } else if (contentType == "application/graphql") {
            return executeRequest(new GraphQLRequest(body), request)
        }

        HttpResponse.unprocessableEntity()
    }

    @Get("/browser")
    HttpResponse browser() {
        if (configuration.enabled && configuration.browser) {
            if (cachedBrowser == null) {
                Optional<URL> url = resourceResolver.getResource(graphiql)
                if (url.isPresent()) {
                    cachedBrowser = url.get()
                }

            }

            if (cachedBrowser != null) {
                return HttpResponse.ok(new StreamedFile(cachedBrowser)).contentType(MediaType.TEXT_HTML + ";charset=utf-8")
            }
        }

        HttpResponse.notFound()
    }

}
