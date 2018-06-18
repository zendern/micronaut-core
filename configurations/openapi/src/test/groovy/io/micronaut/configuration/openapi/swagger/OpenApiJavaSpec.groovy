package io.micronaut.configuration.openapi.swagger

import spock.lang.Specification

class OpenApiJavaSpec extends Specification {

    void "test the output file exists"() {
        given:
        File f = new File("build/classes/java/test/openapi.yaml")

        expect:
        f.exists()
        f.isFile()
    }
}
