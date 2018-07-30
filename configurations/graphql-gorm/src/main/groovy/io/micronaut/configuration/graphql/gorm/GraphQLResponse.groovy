package io.micronaut.configuration.graphql.gorm

import com.fasterxml.jackson.annotation.JsonInclude
import graphql.GraphQLError
import groovy.transform.CompileStatic

@CompileStatic
class GraphQLResponse {

    List<GraphQLError> errors

    @JsonInclude(JsonInclude.Include.ALWAYS)
    Map<String, Object> data
}
