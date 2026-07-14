/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import software.amazon.smithy.java.client.core.ClientConfig;
import software.amazon.smithy.java.client.core.RequestOverrideConfig;
import software.amazon.smithy.java.client.core.interceptors.CallHook;
import software.amazon.smithy.java.client.core.interceptors.ClientInterceptor;
import software.amazon.smithy.java.client.core.interceptors.InputHook;
import software.amazon.smithy.java.client.core.interceptors.OutputHook;
import software.amazon.smithy.java.client.core.interceptors.RequestHook;
import software.amazon.smithy.java.client.core.interceptors.ResponseHook;
import software.amazon.smithy.java.context.Context;
import software.amazon.smithy.java.core.schema.ApiOperation;
import software.amazon.smithy.java.core.schema.SerializableStruct;
import software.amazon.smithy.java.http.api.HttpMessage;
import software.amazon.smithy.java.http.api.HttpRequest;
import software.amazon.smithy.java.http.api.HttpResponse;
import software.amazon.smithy.java.http.api.ModifiableHttpRequest;
import software.amazon.smithy.java.logging.InternalLogger;

/**
 * Records every call into {@link InspectorState}, applies armed inject/rewrite rules, and parks
 * calls at armed breakpoints. Registered by {@link InspectorPlugin}.
 *
 * <p>Breakpoints and injected latency block the calling client thread. Every wait is bounded and
 * fails open — a call is never wedged permanently by the inspector.
 */
final class InspectorInterceptor implements ClientInterceptor {

    private static final InternalLogger LOGGER = InternalLogger.getLogger(InspectorInterceptor.class);
    static final Context.Key<InspectorState.CallRecord> RECORD = Context.key("smithy.inspector.record");

    private final InspectorState state;

    InspectorInterceptor(InspectorState state) {
        this.state = state;
    }

    @Override
    public ClientConfig modifyBeforeCall(CallHook<?, ?> hook) {
        var config = hook.config();
        var record = new InspectorState.CallRecord();
        record.callId = state.nextCallId();
        record.operation = hook.operation().name();
        record.service = safeServiceName(hook.operation());
        record.endpoint = String.valueOf(config.endpointResolver());
        state.setLastConfig(snapshotConfig(config, record.service));
        // Stash the record so later hooks on this call can enrich it.
        return config.withRequestOverride(
                RequestOverrideConfig.builder()
                        .putConfig(RECORD, record)
                        .build());
    }

    @Override
    public boolean interceptCalls() {
        return true;
    }

    /**
     * Owns the call lifecycle. This wraps the entire call synchronously on the calling thread and,
     * critically, catches errors thrown by <em>any</em> hook — including {@code modifyBeforeTransmit},
     * where fault injection short-circuits. The pipeline's {@code readAfterExecution} only fires for
     * failures at or after response deserialization, so finalizing here (not there) is what lets an
     * injected fault be recorded as an {@code error} rather than left {@code in-progress}.
     */
    @Override
    public <I extends SerializableStruct,
            O extends SerializableStruct> O interceptCall(
                    InputHook<I, O> hook,
                    NextCall<I, O> next
            ) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            record.startNanos = System.nanoTime();
            state.addCall(record);
            // Correlate any logs emitted on this thread during the call to this call id.
            state.setCurrentCallId(record.callId);
        }
        RuntimeException thrown = null;
        try {
            return next.invoke(hook);
        } catch (RuntimeException e) {
            thrown = e;
            throw e;
        } finally {
            if (record != null) {
                record.durationMillis = (System.nanoTime() - record.startNanos) / 1_000_000L;
                if (thrown != null) {
                    record.status = "error";
                    record.errorType = thrown.getClass().getName();
                    record.errorMessage = thrown.getMessage();
                } else {
                    record.status = "success";
                }
                state.setCurrentCallId(null);
            }
        }
    }

    @Override
    public void readBeforeSerialization(InputHook<?, ?> hook) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            record.serializeStartNanos = System.nanoTime();
        }
    }

    @Override
    public void readAfterSerialization(RequestHook<?, ?, ?> hook) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            recordPhase(record, "serialize", millisSince(record.serializeStartNanos));
            // Capture the request here — this is the earliest hook where content-length is
            // populated, and it always runs before any transmit-stage fault short-circuits the
            // call, so request bytes are recorded even for injected failures.
            captureRequest(record, hook);
        }
    }

    @Override
    public void readBeforeAttempt(RequestHook<?, ?, ?> hook) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            record.attempts.incrementAndGet();
            record.attemptStartNanos = System.nanoTime();
        }
    }

    @Override
    public void readBeforeSigning(RequestHook<?, ?, ?> hook) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            record.signingStartNanos = System.nanoTime();
        }
    }

    @Override
    public void readAfterSigning(RequestHook<?, ?, ?> hook) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            recordPhase(record, "signing", millisSince(record.signingStartNanos));
        }
    }

    @Override
    public void readBeforeDeserialization(ResponseHook<?, ?, ?, ?> hook) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            record.deserializeStartNanos = System.nanoTime();
        }
    }

    @Override
    public void readAfterDeserialization(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var record = hook.context().get(RECORD);
        if (record != null) {
            recordPhase(record, "deserialize", millisSince(record.deserializeStartNanos));
        }
    }

    @Override
    public void readAfterAttempt(OutputHook<?, ?, ?, ?> hook, RuntimeException error) {
        var record = hook.context().get(RECORD);
        if (record != null && record.attemptStartNanos > 0) {
            recordPhase(record, "attempt", millisSince(record.attemptStartNanos));
        }
    }

    @Override
    public <RequestT> RequestT modifyBeforeTransmit(RequestHook<?, ?, RequestT> hook) {
        var record = hook.context().get(RECORD);
        var operation = hook.operation().name();

        // 1) Breakpoint: park the thread here until resumed or timed out (fails open).
        var bp = state.matchBreakpoint("beforeTransmit", operation);
        if (bp != null) {
            var patch = park(bp, hook, operation, "beforeTransmit");
            if (patch != null) {
                hook = applyPatch(hook, patch);
            }
        }

        // 2) Rewrite rule.
        var rewrite = state.consumeRule(InspectorState.Rule.Kind.REWRITE, operation);
        if (rewrite != null) {
            hook = applyPatch(hook, new InspectorState.RequestPatch(rewrite.endpoint, rewrite.setHeaders));
        }

        // 3) Fault rule: optional latency, then short-circuit by throwing.
        var fault = state.consumeRule(InspectorState.Rule.Kind.FAULT, operation);
        if (fault != null) {
            if (fault.latencyMillis > 0) {
                sleep(fault.latencyMillis);
            }
            if (record != null) {
                record.phaseTimings.put("faultInjected", 1L);
            }
            throw buildFault(fault);
        }

        // Re-capture after any rewrite so the record reflects the request actually sent.
        captureRequest(record, hook);
        return hook.request();
    }

    private static void captureRequest(InspectorState.CallRecord record, RequestHook<?, ?, ?> hook) {
        if (record != null && hook.request() instanceof HttpRequest request) {
            record.requestMethod = request.method();
            record.requestUri = String.valueOf(request.uri());
            record.requestHeaders = headerMap(request);
            record.requestContentLength = messageBytes(request);
        }
    }

    /**
     * Best-effort payload size: prefer the Content-Length header, then fall back to the body's
     * known length. The header is often unset on the client request until the transport writes it,
     * so the body length is the reliable source for buffered payloads. Returns -1 if unknown
     * (e.g. streaming bodies).
     */
    private static long messageBytes(HttpMessage message) {
        var header = message.contentLength();
        if (header != null) {
            return header;
        }
        var body = message.body();
        if (body != null && body.hasKnownLength()) {
            return body.contentLength();
        }
        return -1;
    }

    @Override
    public void readAfterTransmit(ResponseHook<?, ?, ?, ?> hook) {
        var record = hook.context().get(RECORD);
        if (record != null && hook.response() instanceof HttpResponse response) {
            record.responseStatus = response.statusCode();
            record.responseHeaders = headerMap(response);
            record.responseContentLength = messageBytes(response);
        }
    }

    private static void recordPhase(InspectorState.CallRecord record, String phase, long millis) {
        if (record != null) {
            record.phaseTimings.put(phase, millis);
        }
    }

    private static long millisSince(long startNanos) {
        return startNanos <= 0 ? 0 : (System.nanoTime() - startNanos) / 1_000_000L;
    }

    // ---- Breakpoint parking ----

    private InspectorState.RequestPatch park(
            InspectorState.Breakpoint bp,
            RequestHook<?, ?, ?> hook,
            String operation,
            String phase
    ) {
        var frame = new InspectorState.PausedFrame();
        frame.operation = operation;
        frame.service = safeServiceName(hook.operation());
        frame.phase = phase;
        frame.parkedAtMillis = System.currentTimeMillis();
        if (hook.request() instanceof HttpRequest request) {
            frame.requestMethod = request.method();
            frame.requestUri = String.valueOf(request.uri());
            frame.requestHeaders = headerMap(request);
        }
        var id = state.parkFrame(frame);
        LOGGER.debug("Inspector parked call {} at breakpoint {}", operation, bp.breakpointId);
        try {
            if (!frame.latch.await(bp.timeoutMillis, TimeUnit.MILLISECONDS)) {
                // Fail open: never wedge the call.
                state.unparkFrame(id);
                LOGGER.debug("Inspector breakpoint {} timed out; failing open", bp.breakpointId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.unparkFrame(id);
        }
        return frame.patch;
    }

    // ---- Request patching ----

    @SuppressWarnings("unchecked")
    private <RequestT> RequestHook<?, ?, RequestT> applyPatch(
            RequestHook<?, ?, RequestT> hook,
            InspectorState.RequestPatch patch
    ) {
        if (!(hook.request() instanceof HttpRequest httpRequest)) {
            return hook;
        }
        ModifiableHttpRequest modifiable = httpRequest.toModifiableCopy();
        if (patch.setHeaders != null) {
            for (var e : patch.setHeaders.entrySet()) {
                modifiable.headers().setHeader(e.getKey(), e.getValue());
            }
        }
        // Endpoint/URI rewriting is intentionally left as a follow-up: SmithyUri parsing lives
        // behind the endpoint resolver, and rewriting it correctly means going through
        // modifyBeforeCall on the resolver rather than mutating the URI in place here.
        return (RequestHook<?, ?, RequestT>) hook.withRequest((RequestT) modifiable);
    }

    private RuntimeException buildFault(InspectorState.Rule fault) {
        if (fault.throwable != null) {
            try {
                var cls = Class.forName(fault.throwable);
                var thrown = (Throwable) cls.getConstructor(String.class)
                        .newInstance("Injected by SdkInspector rule " + fault.ruleId);
                if (thrown instanceof RuntimeException re) {
                    return re;
                }
                return new InspectorFaultException("Injected fault: " + fault.throwable, thrown);
            } catch (ReflectiveOperationException e) {
                return new InspectorFaultException(
                        "Could not instantiate injected throwable " + fault.throwable,
                        e);
            }
        }
        var status = fault.httpStatus == null ? 500 : fault.httpStatus;
        return new InspectorFaultException(
                "Injected HTTP " + status + " by SdkInspector rule " + fault.ruleId,
                null);
    }

    /** Exception used to short-circuit a call when a fault is injected. */
    static final class InspectorFaultException extends RuntimeException {
        InspectorFaultException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ---- Helpers ----

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safeServiceName(
            ApiOperation<?, ?> operation
    ) {
        try {
            return operation.schema().id().getNamespace();
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static Map<String, String> headerMap(HttpMessage message) {
        var out = new LinkedHashMap<String, String>();
        for (var e : message.headers().map().entrySet()) {
            out.put(e.getKey(), String.join(",", e.getValue()));
        }
        return out;
    }

    private static InspectorState.ConfigRecord snapshotConfig(ClientConfig config, String service) {
        var record = new InspectorState.ConfigRecord();
        record.service = service;
        record.protocol = String.valueOf(config.protocol());
        record.transport = String.valueOf(config.transport());
        record.endpointResolver = String.valueOf(config.endpointResolver());
        record.authSchemes = config.supportedAuthSchemes().stream().map(String::valueOf).toList();
        record.identityResolvers = config.identityResolvers().stream().map(String::valueOf).toList();
        // retryStrategy() is package-private on ClientConfig; expose via toBuilder if needed later.
        record.retryStrategy = "n/a";
        return record;
    }
}
