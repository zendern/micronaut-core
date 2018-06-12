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

package io.micronaut.core.util;

import java.net.URI;

/**
 * URI utils.
 *
 * @author Iván López
 * @since 1.0
 */
public class URIUtils {

    /**
     * Resolve a URI with base and relative parts adding and removing missing leading and trailing
     * slashes to build the expected URI.
     *
     * @param baseURI     The base URI
     * @param relativeURI The relative URI
     * @return The resolved URI
     */
    public static URI resolve(URI baseURI, URI relativeURI) {

        if (endsWithSlash(baseURI) && !startsWithSlash(relativeURI)) {
            return baseURI.resolve(relativeURI);
        } else if (endsWithSlash(baseURI) && startsWithSlash(relativeURI)) {
            return baseURI.resolve(removeLeadingSlash(relativeURI));
        } else if (!endsWithSlash(baseURI) && !startsWithSlash(relativeURI)) {
            return addTrailingSlash(baseURI).resolve(relativeURI);
        } else if (!endsWithSlash(baseURI) && startsWithSlash(relativeURI)) {
            return addTrailingSlash(baseURI).resolve(removeLeadingSlash(relativeURI));
        }

        return baseURI;
    }

    /**
     * Resolve a URI with base and relative parts adding and removing missing leading and trailing
     * slashes to build the expected URI.
     *
     * @param baseURI     The base URI
     * @param relativeURI The relative URI as stringO
     * @return The resolved URI
     */
    public static URI resolve(URI baseURI, String relativeURI) {
        return resolve(baseURI, URI.create(relativeURI));
    }

    private static boolean startsWithSlash(URI uri) {
        String uriString = uri.toString();
        return uriString.length() > 0 && uriString.charAt(0) == '/';
    }

    private static boolean endsWithSlash(URI uri) {
        String uriString = uri.toString();
        return uriString.length() > 0 && uriString.charAt(uriString.length() - 1) == '/';
    }

    private static URI removeLeadingSlash(URI uri) {
        String uriString = uri.toString();
        return URI.create(uriString.substring(1, uriString.length()));
    }

    private static URI addTrailingSlash(URI uri) {
        String uriString = uri.toString();
        return URI.create(uriString + "/");
    }
}
