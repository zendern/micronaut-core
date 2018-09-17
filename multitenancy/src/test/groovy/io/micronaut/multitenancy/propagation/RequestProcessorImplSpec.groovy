package io.micronaut.multitenancy.propagation

import io.micronaut.http.util.RequestProcessorImpl
import io.micronaut.http.util.RequestProcessorMatcher
import spock.lang.Specification
import spock.lang.Unroll

class RequestProcessorImplSpec extends Specification {

    @Unroll("for a request with service #serviceId uri #uri given: serviceregex: #serviceIdRegex uriRegex: #uriRegex #description")
    void "test shouldProcessRequest"(boolean expected,
                                     String serviceId,
                                     String uri,
                                     String serviceIdRegex,
                                     String uriRegex,
                                     String description) {
        when:
        RequestProcessorImpl requestProcessor = new RequestProcessorImpl()

        then:
        requestProcessor.shouldProcessRequest(new MockRequestMatcher(serviceIdRegex: serviceIdRegex, uriRegex: uriRegex), Optional.of(serviceId), uri) == expected

        where:
        expected || serviceId                             | uri             | serviceIdRegex           | uriRegex
        true     || 'https://mymicroservice.micronaut.io' | '/api/books'    | 'https://.*.micronaut.io'| null
        false    || 'https://google.com'                  | '/api/books'    | 'https://.*.micronaut.io'| null
        true     || 'https://mymicroservice.micronaut.io' | '/api/books'    | null                     | '^.*books$'
        false    || 'https://mymicroservice.micronaut.io' | '/api/invoices' | null                     | '^.*books$'


        description = expected ? 'should be processed' : ' should NOT be processed'
    }
}

class MockRequestMatcher implements RequestProcessorMatcher {
    String serviceIdRegex

    String uriRegex
}
