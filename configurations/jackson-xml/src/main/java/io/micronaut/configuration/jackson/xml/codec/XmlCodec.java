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

package io.micronaut.configuration.jackson.xml.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micronaut.core.io.buffer.ByteBuffer;
import io.micronaut.core.io.buffer.ByteBufferFactory;
import io.micronaut.core.type.Argument;
import io.micronaut.http.MediaType;
import io.micronaut.http.codec.CodecException;
import io.micronaut.http.codec.MediaTypeCodec;

import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Allow encoding objects using {@link MediaType#APPLICATION_XML}.
 *
 * @author Iván López
 * @since 1.0
 */
@Singleton
public class XmlCodec implements MediaTypeCodec {

    private final XmlMapper xmlMapper;

    /**
     * Default constructor.
     *
     * @param xmlMapper The xmlMapper
     */
    public XmlCodec(XmlMapper xmlMapper) {
        this.xmlMapper = xmlMapper;
    }

    @Override
    public MediaType getMediaType() {
        return MediaType.APPLICATION_XML_TYPE;
    }

    @SuppressWarnings("Duplicates")
    @Override
    public <T> T decode(Argument<T> type, InputStream inputStream) throws CodecException {
        try {
            if (type.hasTypeVariables()) {
                JavaType javaType = constructJavaType(type);
                return xmlMapper.readValue(inputStream, javaType);
            } else {
                return xmlMapper.readValue(inputStream, type.getType());
            }
        } catch (IOException e) {
            throw new CodecException("Error decoding XML for type [" + type.getName() + "]: " + e.getMessage());
        }
    }

    @Override
    public <T> void encode(T object, OutputStream outputStream) throws CodecException {
        try {
            xmlMapper.writeValue(outputStream, object);
        } catch (IOException e) {
            throw new CodecException("Error encoding object [" + object + "] to XML: " + e.getMessage());
        }
    }

    @Override
    public <T> byte[] encode(T object) throws CodecException {
        try {
            if (object instanceof byte[]) {
                return (byte[]) object;
            } else {
                return xmlMapper.writeValueAsBytes(object);
            }
        } catch (JsonProcessingException e) {
            throw new CodecException("Error encoding object [" + object + "] to XML: " + e.getMessage());
        }
    }

    @Override
    public <T> ByteBuffer encode(T object, ByteBufferFactory allocator) throws CodecException {
        byte[] bytes = encode(object);
        return allocator.copiedBuffer(bytes);
    }

    private <T> JavaType constructJavaType(Argument<T> type) {
        Map<String, Argument<?>> typeVariables = type.getTypeVariables();
        TypeFactory typeFactory = xmlMapper.getTypeFactory();
        JavaType[] objects = toJavaTypeArray(typeFactory, typeVariables);
        return typeFactory.constructParametricType(
            type.getType(),
            objects
        );
    }

    private JavaType[] toJavaTypeArray(TypeFactory typeFactory, Map<String, Argument<?>> typeVariables) {
        List<JavaType> javaTypes = new ArrayList<>();
        for (Argument<?> argument : typeVariables.values()) {
            if (argument.hasTypeVariables()) {
                javaTypes.add(typeFactory.constructParametricType(argument.getType(), toJavaTypeArray(typeFactory, argument.getTypeVariables())));
            } else {
                javaTypes.add(typeFactory.constructType(argument.getType()));
            }
        }
        return javaTypes.toArray(new JavaType[javaTypes.size()]);
    }
}
