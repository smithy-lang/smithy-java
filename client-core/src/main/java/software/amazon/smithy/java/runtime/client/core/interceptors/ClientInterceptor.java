/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.runtime.client.core.interceptors;

import static software.amazon.smithy.java.runtime.core.Context.Value;

import java.util.Arrays;
import java.util.List;
import software.amazon.smithy.java.runtime.core.Context;
import software.amazon.smithy.java.runtime.core.Either;
import software.amazon.smithy.java.runtime.core.schema.SdkException;
import software.amazon.smithy.java.runtime.core.schema.SerializableShape;

/**
 * An interceptor allows injecting code into the clien'ts request execution pipeline.
 *
 * <p>Terminology:
 *
 * <ul>
 *     <li>Input: The modeled input of an operation.</li>
 *     <li>Output: The modeled output of an operation.</li>
 *     <li>Request: A protocol specific request to send.</li>
 *     <li>Response: A protocol specific response from a service.</li>
 *     <li>Error: Modeled or unmodeled errors that can be returned by a client.</li>
 *     <li>Result: Either a successful output or error.</li>
 *     <li>Execution: is one end-to-end invocation against a client.</li>
 *     <li>Attempt: attempt at performing an execution. By default, executions are retried multiple times based on the
 *     client's retry strategy.</li>
 *     <li>Hook: a single method on the interceptor, allowing injection of code into a specific part of the client's
 *     request execution pipeline. Hooks are either "read" hooks, which make it possible to read in-flight request or
 *     response messages, or "read/write" hooks, which make it possible to modify in-flight request, response, input,
 *     output, or errors.</li>
 * </ul>
 */
public interface ClientInterceptor {

    /**
     * An interceptor that does nothing.
     */
    ClientInterceptor NOOP = new ClientInterceptor() {};

    /**
     * Combines multiple interceptors into a single interceptor.
     *
     * @param interceptors Interceptors to combine.
     * @return the combined interceptors.
     */
    static ClientInterceptor chain(List<ClientInterceptor> interceptors) {
        return new ClientInterceptorChain(interceptors);
    }

    /**
     * Combines multiple interceptors into a single interceptor.
     *
     * @param interceptors Interceptors to combine.
     * @return the combined interceptors.
     */
    static ClientInterceptor chain(ClientInterceptor... interceptors) {
        return chain(Arrays.asList(interceptors));
    }

    /**
     * A hook called at the start of an execution, before the client does anything else.
     *
     * <p>When: This will ALWAYS be called once per execution. The duration between invocation of this hook and
     * {@link #readAfterExecution} is very close to the full duration of the execution.
     *
     * <p>Errors raised by this hook will be stored until all interceptors have had their {@code readBeforeExecution}
     * invoked. Other hooks will then be skipped, and execution will jump to {@link #modifyBeforeCompletion}
     * with the raised error as the result value. If multiple {@code readBeforeExecution} methods raise errors,
     * the latest will be used, and earlier errors will be logged and dropped.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param <I> Input type.
     */
    default <I extends SerializableShape> void readBeforeExecution(Context context, I input) {}

    /**
     * A hook called before the input message is serialized into a transport message.
     *
     * <p>This method can modify and return a new request message of the same type.
     *
     * <p>When: This will ALWAYS be called once per execution, except when a failure occurs earlier in the request
     * pipeline.
     *
     * <p>Available Information: Input is ALWAYS available. This input may have been modified by earlier
     * {@code modifyBeforeSerialization} hooks, and may be modified further by later hooks. Other information is not
     * available.
     *
     * <p>Error Behavior: If a hook raises an error, execution will jump to {@link #modifyBeforeCompletion} with the
     * raised error as the {@code result}.
     *
     * <p>Return Constraints: The input message returned by this hook MUST be the same type of input message passed
     * into this hook. If not, an error will immediately be raised.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @return the updated input.
     * @param <I> Input type.
     */
    default <I extends SerializableShape> I modifyBeforeSerialization(Context context, I input) {
        return input;
    }

    /**
     * A hook called before the input is serialized into a transport request.
     *
     * <p>When: This will ALWAYS be called once per execution, except when a failure occurs earlier in the request
     * pipeline. The duration between invocation of this hook and afterSerialization is very close to the amount of
     * time spent deserializing the request.
     *
     * <p>Available Information: input is ALWAYS available. Other information is not available.
     *
     * <p>Error Behavior: If a hook raises an error, execution will jump to {@link #modifyBeforeCompletion} with the
     * raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param <I> Input type.
     */
    default <I extends SerializableShape> void readBeforeSerialization(Context context, I input) {}

    /**
     * A hook called after the input message is marshalled into a protocol-specific request.
     *
     * <p>When: This will ALWAYS be called once per execution, except when a failure occurs earlier in the request
     * pipeline. The duration between invocation of this hook and {@link #readBeforeSerialization} is very close to the
     * amount of time spent serializing the request.
     *
     * <p>Available Information: The input and protocol-specific request are ALWAYS available. Other information is not
     * available.
     *
     * <p>Error Behavior: If a hook raises an error, execution will jump to {@link #modifyBeforeCompletion} with the
     * raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> void readAfterSerialization(
        Context context,
        I input,
        Value<RequestT> request
    ) {}

    /**
     * A hook called before the retry loop is entered that can be used to modify and return a new request.
     *
     * <p>When: This will ALWAYS be called once per execution, except when a failure occurs earlier in the request
     * pipeline.
     *
     * <p>Available Information: The input and request are ALWAYS available. Other information is not available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeCompletion} with
     * the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @return the updated protocol-specific, type-safe request entry to send.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> Value<RequestT> modifyBeforeRetryLoop(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        return request;
    }

    /**
     * A hook called before each attempt at sending the transmission * request message to the service.
     *
     * <p>When: This will ALWAYS be called once per attempt, except when a
     * failure occurs earlier in the request pipeline. This method will be
     * called multiple times when retries occur.
     *
     * <p>Available Information: The input and request are ALWAYS available. Other information is not available.
     *
     * <p>Error Behavior: Errors raised by this hook will be stored until all interceptors have had their
     * {@code #readBeforeAttempt} invoked. Other hooks will then be skipped, and execution will jump to
     * {@link #modifyBeforeAttemptCompletion} with the raised error as the result. If multiple {@code beforeAttempt}
     * methods raise errors, the latest is used, and earlier errors are logged and dropped.
     * 
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> void readBeforeAttempt(
        Context context,
        I input,
        Value<RequestT> request
    ) {}

    /**
     * A hook called before the request is signed; this method can modify and return a new request of the same type.
     *
     * <p>When: This is ALWAYS called once per attempt, except when a failure occurs earlier in the request pipeline.
     * This method may be called multiple times if retries occur.
     *
     * <p>Available Information: The input and request are ALWAYS available. The request may have been modified by
     * earlier {@code modifyBeforeSigning} hooks, and may be modified further by later hooks. Other information is not
     * available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @return the type-safe, protocol-specific request.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> Value<RequestT> modifyBeforeSigning(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        return request;
    }

    /**
     * A hook called before the transport request message is signed.
     *
     * <p>When: This is ALWAYS called once per attempt, except when a failure occurs earlier in the request pipeline.
     * This method may be called multiple times when retries occur. The duration between invocation of this hook and
     * {@link #readAfterSigning} is very close to the amount of time spent signing the request.
     *
     * <p>Available Information: The input and request are ALWAYS available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @param <I>Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> void readBeforeSigning(
        Context context,
        I input,
        Value<RequestT> request
    ) {}

    /**
     * A hook called after the transport request message is signed.
     *
     * <p>When: This is ALWAYS called once per attempt, except when a failure occurs earlier in the request
     * pipeline. This method may be called multiple times when retries occur. The duration between invocation of this
     * hook and {@link #readBeforeSigning} is very close to the amount of time spent signing the request.
     *
     * <p>Available Information: The input and request are ALWAYS available. Other information is not available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> void readAfterSigning(
        Context context,
        I input,
        Value<RequestT> request
    ) {}

    /**
     * A hook called before the transport request message is sent to the service.
     *
     * <p>This method can modify and return a new protocol-specific request of the same type.
     *
     * <p>When: This is ALWAYS called at least once per attempt, except when a failure occurs earlier in the request
     * pipeline. This method may be called multiple times when retries occur.
     *
     * <p>Available Information: The input and request are always available. The request may have been modified by
     * earlier {@code modifyBeforeTransmit} hooks, and may be modified further by later hooks. Other information
     * is not available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @return the type-safe, protocol-specific request.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> Value<RequestT> modifyBeforeTransmit(
        Context context,
        I input,
        Value<RequestT> request
    ) {
        return request;
    }

    /**
     * A hook called before the transport request message is sent to the * service.
     *
     * <p>When: This is ALWAYS called once per attempt, except when a failure occurs earlier in the request
     * pipeline. This method may be called multiple times when retries occur. The duration between invocation of this
     * hook and {@link #readAfterTransmit} is very close to the amount of time spent communicating with the service.
     * Depending on the protocol, the duration may not include the time spent reading the response data.
     *
     * <p>Available Information: The input and request are ALWAYS available. Other information is not available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request to send.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     */
    default <I extends SerializableShape, RequestT> void readBeforeTransmit(
        Context context,
        I input,
        Value<RequestT> request
    ) {}

    /**
     * A hook called after the transport request message is sent to the service and a transport response message is
     * received.
     *
     * <p>When: This is ALWAYS called once per attempt, except when a failure occurs earlier in the request pipeline.
     * This method may be called multiple times when retries occur. The duration between invocation of this hook and
     * {@link #readBeforeTransmit} is very close to the amount of time spent communicating with the service.
     * Depending on the protocol, the duration may not include the time spent reading the response data.
     *
     * <p>Available Information: The input, request, and response are ALWAYS available. Other information is not
     * available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request that was sent.
     * @param response Protocol-specific, type-safe response that was received.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, RequestT, ResponseT> void readAfterTransmit(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response
    ) {}

    /**
     * A hook called before the response is deserialized.
     *
     * <p>This method can modify and return a new response of the same type.
     *
     * <p>When: This is ALWAYS called once per attempt, except when a failure occurs earlier in the request pipeline.
     * This method may be called multiple times when retries occur.
     *
     * <p>Available Information: The input, request, and response are ALWAYS available. The response may have been
     * modified by earlier {@code modifyBeforeDeserialization} hooks, and may be modified further by later hooks. Other
     * information is not available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request that was sent.
     * @param response Protocol-specific, type-safe response that was received.
     * @return the type-safe, protocol-specific response.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, RequestT, ResponseT> Value<ResponseT> modifyBeforeDeserialization(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response
    ) {
        return response;
    }

    /**
     * A hook called before the response is deserialized.
     *
     * <p>When: This is ALWAYS called once per attempt, except when a failure occurs earlier in the request pipeline.
     * This method may be called multiple times when retries occur. The duration between invocation of this hook and
     * {@link #readAfterDeserialization} is very close to the amount of time spent unmarshalling the service response.
     * Depending on the protocol and operation, the duration may include the time spent downloading the response data.
     *
     * <p>Available Information: The input, request, and response are ALWAYS available. Other information is not
     * available.
     *
     * <p>Error Behavior: If this hook raises an error, execution will jump to {@link #modifyBeforeAttemptCompletion}
     * with the raised error as the {@code result}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request that was sent.
     * @param response Protocol-specific, type-safe response that was received.
     * @param <I> Input type.
     * @param <RequestT> Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, RequestT, ResponseT> void readBeforeDeserialization(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response
    ) {}

    /**
     * A hook called after the transport response message is deserialized.
     *
     * <p>When: This hook is ALWAYS called once per attempt, except when a failure occurs earlier in the request
     * pipeline. The duration between invocation of this hook and {@link #readBeforeDeserialization} is very close to
     * the amount of time spent deserializing the service response. Depending on the protocol and operation, the
     * duration may include the time spent downloading the response data.
     *
     * <p>Available Information: The input, request, response, and result are always available.
     *
     * <p>Error Behavior: If this hook raises or returns an error, execution will jump to
     * {@link #modifyBeforeAttemptCompletion} with the raised error as the result.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request that was sent.
     * @param response Protocol-specific, type-safe response that was received.
     * @param result The result that contains either the modeled output, or an error.
     * @param <I> Input type.
     * @param <O> Output type.
     * @param <RequestT> Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> void readAfterDeserialization(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response,
        Either<SdkException, O> result
    ) {}

    /**
     * A hook called when an attempt is completed. This method can modify and return a new output or error matching
     * the currently executing operation.
     *
     * <p>When: This hook is ALWAYS called once per attempt, except when a failure occurs before
     * {@link #readBeforeAttempt}. This method may be called multiple times when retries occur.
     *
     * <p>Available Information: The input, request, response, and result are ALWAYS available.
     *
     * <p>Error Behavior: If this hook raises or returns an exception, execution will jump to {@link #readAfterAttempt}
     * with the error as the result.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request that was sent.
     * @param response Protocol-specific, type-safe response that was received.
     * @param result The result that contains either the modeled output, or an error.
     * @return either the modeled output or an error to associate with the call.
     * @param <I> Input type.
     * @param <O> Output type.
     * @param <RequestT> Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> Either<SdkException, O> modifyBeforeAttemptCompletion(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> response,
        Either<SdkException, O> result
    ) {
        return result;
    }

    /**
     * A hook called when an attempt is completed.
     *
     * <p>When: This will ALWAYS be called once per attempt, as long as beforeAttempt has been executed.
     *
     * <p>Available Information: The input, request, and result are ALWAYS available. The response is available if a
     * response was received by the service for this attempt.
     *
     * <p>Error Behavior: Errors raised by or returned by this hook are stored until all interceptors have had their
     * {@code readAfterAttempt} invoked. If multiple {@code readAfterAttempt} methods raise errors, the latest
     * is used, and earlier errors are logged and dropped. If the retry strategy determines that the response is
     * retryable, execution will then jump to {@link #readBeforeAttempt}. Otherwise, execution will jump to
     * {@link #modifyBeforeCompletion}.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param request Protocol-specific request that was sent.
     * @param responseIfAvailable Protocol-specific, type-safe response that was received.
     * @param result The result that contains either the modeled output, or an error.
     * @param <I> Input type.
     * @param <O> Output type.
     * @param <RequestT>  Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> void readAfterAttempt(
        Context context,
        I input,
        Value<RequestT> request,
        Value<ResponseT> responseIfAvailable,
        Either<SdkException, O> result
    ) {}

    /**
     * A hook called when an execution is completed.
     *
     * <p>This method can modify and return a new output or error matching the currently executing operation.
     *
     * <p>When: This will ALWAYS be called once per execution.
     *
     * <p>Available Information: The input and result are ALWAYS available. The request and response are available
     * if the execution proceeded far enough for them to be generated.
     *
     * <p>Error Behavior: If this hook raises or returns an error, execution will jump to {@link #readAfterExecution}
     * with the raised error as the result.
     *
     * @param context             Mutable, thread-safe execution context data.
     * @param input               The modeled input of the call.
     * @param requestIfAvailable Protocol-specific request that was sent, or null if not available.
     * @param responseIfAvailable Protocol-specific, type-safe response that was received, or null if not available.
     * @param result              The result that contains either the modeled output, or an error.
     * @return either the modeled output or an error to associate with the call.
     * @param <I> Input type.
     * @param <O> Output type.
     * @param <RequestT>  Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> Either<SdkException, O> modifyBeforeCompletion(
        Context context,
        I input,
        Value<RequestT> requestIfAvailable,
        Value<ResponseT> responseIfAvailable,
        Either<SdkException, O> result
    ) {
        return result;
    }

    /**
     * A hook called when an execution is completed.
     *
     * <p>When: This is ALWAYS called once per execution. The duration * between invocation of this hook and
     * beforeExecution is very close to the full duration of the execution.
     *
     * <p>Available Information: The input and result are ALWAYS available. The request and response are available if
     * the execution proceeded far enough for them to be generated.
     *
     * <p>Error Behavior: Errors raised by or returned by this hook will be stored until all interceptors have had
     * their {@code readAfterExecution} hook invoked. The error will then be treated as the result to the caller.
     * If multiple {@code readAfterExecution} methods raise an error, the latest is used, and earlier errors are logged
     * and dropped.
     *
     * @param context Mutable, thread-safe execution context data.
     * @param input The modeled input of the call.
     * @param requestIfAvailable Protocol-specific request that was sent, or null if not available.
     * @param responseIfAvailable Protocol-specific, type-safe response that was received, or null if not available.
     * @param result The result that contains either the modeled output, or an error.
     * @param <I> Input type.
     * @param <O> Output type.
     * @param <RequestT>  Protocol-specific request type.
     * @param <ResponseT> Protocol-specific response type.
     */
    default <I extends SerializableShape, O extends SerializableShape, RequestT, ResponseT> void readAfterExecution(
        Context context,
        I input,
        Value<RequestT> requestIfAvailable,
        Value<ResponseT> responseIfAvailable,
        Either<SdkException, O> result
    ) {}
}
