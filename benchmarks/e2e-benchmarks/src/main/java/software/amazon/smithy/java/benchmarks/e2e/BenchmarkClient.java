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

/**
 * Reuses one generated client per protocol and binds each operation through a {@link MethodHandle} at setup.
 */
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

    /** Points the transport at the case and binds the case's operation method. */
    Call prepare(BenchmarkCase benchmarkCase) {
        if (benchmarkCase.protocol() != protocol) {
            throw new IllegalArgumentException(
                    benchmarkCase.id() + " belongs to " + benchmarkCase.protocol() + ", not " + protocol);
        }
        transport.prepare(benchmarkCase);
        return new Call(
                operationHandle(benchmarkCase.operationName()),
                benchmarkCase.input(),
                benchmarkCase.outputPayloadMember());
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

    /**
     * A bound operation call: the same input on every invocation, with the output's streaming payload (if the
     * operation has one) drained so the response body is actually read.
     */
    static final class Call {
        private final MethodHandle handle;
        private final SerializableStruct input;
        private final Schema outputPayloadMember;
        private byte[] sink;

        private Call(MethodHandle handle, SerializableStruct input, Schema outputPayloadMember) {
            this.handle = handle;
            this.input = input;
            this.outputPayloadMember = outputPayloadMember;
        }

        SerializableStruct invoke() throws Throwable {
            SerializableStruct output = (SerializableStruct) handle.invokeExact(input);
            if (outputPayloadMember != null) {
                Object payload = output.getMemberValue(outputPayloadMember);
                if (payload instanceof DataStream stream) {
                    MockHttpTransport.drain(stream);
                } else if (payload instanceof ByteBuffer buffer) {
                    // Non-streaming blob payload (e.g. GetObject Body): read every byte, as a client
                    // consuming the response does and as ocs requires. The SDK may deserialize it
                    // zero-copy, so without this the payload is never touched and the number is meaningless.
                    int n = buffer.remaining();
                    if (sink == null || sink.length < n) {
                        sink = new byte[n];
                    }
                    buffer.duplicate().get(sink, 0, n);
                }
            }
            return output;
        }
    }
}
