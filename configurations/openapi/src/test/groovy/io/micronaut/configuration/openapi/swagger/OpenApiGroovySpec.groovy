package io.micronaut.configuration.openapi.swagger

import spock.lang.Specification

class OpenApiGroovySpec extends Specification {

    void "test the output matches java"() {
        given:
        File java = new File("build/classes/java/test/openapi.yaml")
        File groovy = new File("build/classes/groovy/test/openapi.yaml")

        expect:
        java.exists()
        java.isFile()
        groovy.exists()
        groovy.isFile()
        java.text == groovy.text
    }
}