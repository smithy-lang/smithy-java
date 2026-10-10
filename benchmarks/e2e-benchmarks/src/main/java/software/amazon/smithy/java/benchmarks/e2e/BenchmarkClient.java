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
import software.amazon.smithy.java.client.core.ClientTransport;
import software.amazon.smithy.java.core.schema.Schema;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.io.datastream.DataStream;

/** One generated client per protocol, built once and reused by every case of that protocol. */
final class BenchmarkClient implements AutoCloseable {

    private static final MethodType GENERIC_CALL = MethodType.methodType(
            SerializableStruct.class,
            SerializableStruct.class);

    private final BenchmarkProtocol protocol;
    private final Object client;

    BenchmarkClient(BenchmarkProtocol protocol, ClientTransport<?, ?> transport, String endpoint) {
        this.protocol = protocol;
        this.client = protocol.newClient(transport, endpoint);
    }

    Call prepare(BenchmarkCase benchmarkCase) {
        if (benchmarkCase.protocol() != protocol) {
            throw new IllegalArgumentException(
                    benchmarkCase.id() + " belongs to " + benchmarkCase.protocol() + ", not " + protocol);
        }
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

    /** One benchmark case bound to its client: the operation method handle and the prepared input. */
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
            // This SDK advances a blob payload's buffer while serializing; rewind it or later calls send an empty body.
            if (requestPayload != null) {
                requestPayload.rewind();
            }
            SerializableStruct output = (SerializableStruct) handle.invokeExact(input);
            if (outputPayloadMember != null) {
                Object payload = output.getMemberValue(outputPayloadMember);
                if (payload instanceof DataStream stream) {
                    Bodies.drain(stream);
                } else if (payload instanceof ByteBuffer buffer) {
                    Bodies.consume(buffer);
                }
            }
            return output;
        }
    }
}
