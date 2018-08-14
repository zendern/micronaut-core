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

    void "test the output file contents"() {
        given:
        File f = new File("build/classes/java/test/openapi.yaml")
        org.yaml.snakeyaml.Yaml yaml = new org.yaml.snakeyaml.Yaml()
        Iterable<Object> objects = yaml.loadAll(f.newInputStream())
        Map data = objects.toList().first()

        expect:
        f.exists()
        f.isFile()
        data.openapi == "3.0.1"
        data.info.title == "test title"
        data.info.version == "1.2.3"
    }
}
