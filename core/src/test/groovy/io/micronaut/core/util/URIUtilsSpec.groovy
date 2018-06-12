/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.micronaut.core.util

import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Iván López
 * @since 1.0
 */
class URIUtilsSpec extends Specification {

    @Unroll
    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/264')
    void 'test URI are resolved correctly no matter the trailing or leading slash'() {
        expect:
        URIUtils.resolve(URI.create(baseUri), URI.create(relativeUri)) == URI.create(expectedUri)
        URIUtils.resolve(URI.create(baseUri), relativeUri) == URI.create(expectedUri)

        where:
        baseUri                        | relativeUri | expectedUri
        'https://stream.meetup.com/2/' | 'rsvps'     | 'https://stream.meetup.com/2/rsvps'
        'https://stream.meetup.com/2/' | '/rsvps'    | 'https://stream.meetup.com/2/rsvps'
        'https://stream.meetup.com/2'  | 'rsvps'     | 'https://stream.meetup.com/2/rsvps'
        'https://stream.meetup.com/2'  | '/rsvps'    | 'https://stream.meetup.com/2/rsvps'
    }
}
