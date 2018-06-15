package io.micronaut.http.server.netty.codec

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class ProduceXmlSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared @AutoCleanup EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()
    @Shared @AutoCleanup HttpClient client = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.getURL())

    void "test xml response content type and conversion"() {
        given:
        HttpResponse response = client.toBlocking().exchange(HttpRequest.GET('/xml'), String)

        expect:
        response.body() == '<Book><title>The Stand</title></Book>'
        response.contentType.isPresent()
        response.contentType.get() == MediaType.APPLICATION_XML_TYPE
    }

    @Controller("/xml")
    static class XmlController {

        @Get(uri = '/', produces = MediaType.APPLICATION_XML)
        Book book() {
            new Book(title: "The Stand")
        }
    }

    static class Book {
        String title
    }
}
