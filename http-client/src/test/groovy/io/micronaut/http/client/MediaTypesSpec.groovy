package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MediaTypesSpec extends Specification {

    @Shared @AutoCleanup EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    void "support parsing JSON from text/javascript"() {
        given:
        JsClient jsClient = embeddedServer.applicationContext.getBean(JsClient)

        expect:
        jsClient.test().name == "Bar"
    }

    @Client('/mediatypes/js')
    static interface JsClient {
        @Get(uri = "/", processes = 'text/javascript')
        Foo test()
    }

    @Controller("/mediatypes/js")
    static class JsController {

        @Get(uri = "/", produces = 'text/javascript')
        Foo test() {
            return new Foo(name: "Bar")
        }

    }

    static class Foo { String name }
}
