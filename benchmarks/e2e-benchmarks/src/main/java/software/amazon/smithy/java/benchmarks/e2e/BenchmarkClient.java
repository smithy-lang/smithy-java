/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.benchmarks.e2e;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;

final class BenchmarkClient implements AutoCloseable {

    private static final MethodType GENERIC_CALL = MethodType.methodType(
            SerializableStruct.class,
            SerializableStruct.class);

    private final BenchmarkProtocol protocol;
    private final CountingTransport transport;
    private final Object client;

    BenchmarkClient(BenchmarkProtocol protocol, CountingTransport transport, String endpoint) {
        this.protocol = protocol;
        this.transport = transport;
        this.client = protocol.newClient(transport, endpoint);
    }

    CountingTransport transport() {
        return transport;
    }

    Call prepare(BenchmarkCase benchmarkCase) {
        if (benchmarkCase.protocol() != protocol) {
            throw new IllegalArgumentException(
                    benchmarkCase.id() + " belongs to " + benchmarkCase.protocol() + ", not " + protocol);
        }
        transport.prepare(benchmarkCase);
        return new Call(
                operationHandle(benchmarkCase.operationName()),
                benchmarkCase.input(),
                benchmarkCase.outputPayloadMember(),
                benchmarkCase.requestPayload());
    }

    private MethodHandle operationHandle(String operationName) {
        Class<?> clientInterface = protocol.clientInterface();
        Method found = null;
        for (Method method : clientInterface.getMethods()) {
            if (method.getParameterCount() == 1
                    && method.getName().equalsIgnoreCase(operationName)
                    && SerializableStruct.class.isAssignableFrom(method.getParameterTypes()[0])) {
                found = method;
                break;
            }
        }
        if (found == null) {
            throw new IllegalStateException(
                    "No single-argument operation method for " + operationName + " on " + clientInterface.getName());
        }
        try {
            return MethodHandles.publicLookup().unreflect(found).bindTo(client).asType(GENERIC_CALL);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access " + found, e);
        }
    }

    @Override
    public void close() {
        if (client instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                throw new IllegalStateException("Failed to close " + protocol + " client", e);
            }
        }
    }

    static final class Call {
        private final MethodHandle handle;
        private final SerializableStruct input;
        private final Schema outputPayloadMember;
        private final ByteBuffer requestPayload;

        private Call(
                MethodHandle handle,
                SerializableStruct input,
                Schema outputPayloadMember,
                ByteBuffer requestPayload
        ) {
            this.handle = handle;
            this.input = input;
            this.outputPayloadMember = outputPayloadMember;
            this.requestPayload = requestPayload;
        }

        SerializableStruct invoke() throws Throwable {
            // The input is reused across calls; serializing a blob payload advances its buffer, so rewind it
            // first or every call after the first serializes an empty body.
            if (requestPayload != null) {
                requestPayload.rewind();
            }
            SerializableStruct output = (SerializableStruct) handle.invokeExact(input);
            if (outputPayloadMember != null) {
                Object payload = output.getMemberValue(outputPayloadMember);
                if (payload instanceof DataStream stream) {
                    MockHttpTransport.drain(stream);
                } else if (payload instanceof ByteBuffer buffer) {
                    // Read every byte because deserialization may leave the payload in a buffer without copying it.
                    MockHttpTransport.consume(buffer);
                }
            }
            return output;
        }
    }
}
