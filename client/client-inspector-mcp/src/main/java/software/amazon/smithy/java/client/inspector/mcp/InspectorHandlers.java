/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.java.core.serde.document.Document;

/**
 * Business logic for each {@link InspectorService} operation: reads and mutates {@link InspectorState}
 * and returns a member map that the service wraps into the operation's output {@code StructDocument}.
 *
 * <p>Kept separate from the service wiring so the tool surface is easy to read as a single file.
 */
final class InspectorHandlers {

    private final InspectorState state;

    InspectorHandlers(InspectorState state) {
        this.state = state;
    }

    Map<String, Document> handle(String operation, Document input) {
        return switch (operation) {
            case "ListCalls" -> listCalls(input);
            case "GetCall" -> getCall(input);
            case "GetConfig" -> getConfig(input);
            case "QueryLogs" -> queryLogs(input);
            case "QueryTelemetry" -> queryTelemetry(input);
            case "InjectFault" -> injectFault(input);
            case "RewriteRequest" -> rewriteRequest(input);
            case "ListRules" -> listRules();
            case "ClearRule" -> clearRule(input);
            case "SetBreakpoint" -> setBreakpoint(input);
            case "ListPausedFrames" -> listPausedFrames();
            case "GetPausedFrame" -> getPausedFrame(input);
            case "Resume" -> resume(input);
            case "ResumeAll" -> resumeAll();
            case "ListBreakpoints" -> listBreakpoints();
            case "RemoveBreakpoint" -> removeBreakpoint(input);
            default -> throw new IllegalArgumentException("Unknown inspector operation: " + operation);
        };
    }

    // ---- Observe ----

    private Map<String, Document> listCalls(Document input) {
        var opFilter = str(input, "operation");
        var errorOnly = boolOrFalse(input, "errorOnly");
        var sinceId = lng(input, "sinceId");
        var limit = intOr(input, "limit", 50);

        var all = state.listCalls();
        var out = new ArrayList<Document>();
        // Most recent first.
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            var c = all.get(i);
            if (opFilter != null && !opFilter.equals(c.operation)) {
                continue;
            }
            if (errorOnly && !"error".equals(c.status)) {
                continue;
            }
            if (sinceId != null && c.callId <= sinceId) {
                continue;
            }
            out.add(callSummary(c));
        }
        return Map.of("calls", Document.of(out));
    }

    private Map<String, Document> getCall(Document input) {
        var c = state.getCall(reqLng(input, "callId"));
        if (c == null) {
            return Map.of();
        }
        var m = new LinkedHashMap<String, Document>();
        put(m, "callId", c.callId);
        put(m, "operation", c.operation);
        put(m, "service", c.service);
        put(m, "status", c.status);
        put(m, "errorType", c.errorType);
        put(m, "errorMessage", c.errorMessage);
        put(m, "attempts", c.attempts.get());
        putIfPositive(m, "durationMillis", c.durationMillis);
        put(m, "endpoint", c.endpoint);
        put(m, "requestMethod", c.requestMethod);
        put(m, "requestUri", c.requestUri);
        putHeaders(m, "requestHeaders", c.requestHeaders);
        putIfPositive(m, "requestContentLength", c.requestContentLength);
        if (c.responseStatus >= 0) {
            put(m, "responseStatus", c.responseStatus);
        }
        putHeaders(m, "responseHeaders", c.responseHeaders);
        putIfPositive(m, "responseContentLength", c.responseContentLength);
        if (!c.phaseTimings.isEmpty()) {
            var timings = new LinkedHashMap<String, Document>();
            c.phaseTimings.forEach((k, v) -> timings.put(k, Document.of(v)));
            m.put("phaseTimings", Document.of(timings));
        }
        return Map.of("call", Document.of(m));
    }

    private Map<String, Document> getConfig(Document input) {
        var cfg = state.lastConfig();
        if (cfg == null) {
            return Map.of();
        }
        var m = new LinkedHashMap<String, Document>();
        put(m, "service", cfg.service);
        put(m, "protocol", cfg.protocol);
        put(m, "transport", cfg.transport);
        put(m, "endpointResolver", cfg.endpointResolver);
        putStrings(m, "authSchemes", cfg.authSchemes);
        putStrings(m, "identityResolvers", cfg.identityResolvers);
        put(m, "retryStrategy", cfg.retryStrategy);
        return Map.of("config", Document.of(m));
    }

    // ---- Logs + telemetry ----

    private Map<String, Document> queryLogs(Document input) {
        var minLevel = str(input, "minLevel");
        var contains = str(input, "contains");
        var callId = lng(input, "callId");
        var limit = intOr(input, "limit", 100);
        int minRank = levelRank(minLevel);

        var all = state.listLogs();
        var out = new ArrayList<Document>();
        for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
            var e = all.get(i);
            if (levelRank(e.level) < minRank) {
                continue;
            }
            if (contains != null && (e.message == null || !e.message.contains(contains))) {
                continue;
            }
            if (callId != null && (e.callId == null || e.callId != callId.longValue())) {
                continue;
            }
            var m = new LinkedHashMap<String, Document>();
            put(m, "level", e.level);
            put(m, "logger", e.logger);
            put(m, "message", e.message);
            put(m, "epochMillis", e.epochMillis);
            put(m, "thrown", e.thrown);
            if (e.callId != null) {
                put(m, "callId", e.callId);
            }
            out.add(Document.of(m));
        }
        return Map.of("logs", Document.of(out));
    }

    private Map<String, Document> queryTelemetry(Document input) {
        var opFilter = str(input, "operation");
        // Group recorded calls by operation and aggregate.
        Map<String, List<InspectorState.CallRecord>> byOp = new LinkedHashMap<>();
        for (var c : state.listCalls()) {
            if (opFilter != null && !opFilter.equals(c.operation)) {
                continue;
            }
            byOp.computeIfAbsent(c.operation, k -> new ArrayList<>()).add(c);
        }
        var out = new ArrayList<Document>();
        for (var entry : byOp.entrySet()) {
            var calls = entry.getValue();
            long count = calls.size();
            long errors = calls.stream().filter(c -> "error".equals(c.status)).count();
            var durations = calls.stream().map(c -> c.durationMillis).filter(d -> d >= 0).sorted().toList();
            long p50 = durations.isEmpty() ? -1 : durations.get(durations.size() / 2);
            long max = durations.stream().mapToLong(Long::longValue).max().orElse(-1);
            double avgAttempts = calls.stream().mapToInt(c -> c.attempts.get()).average().orElse(0);
            long avgReq = avg(calls.stream().mapToLong(c -> c.requestContentLength).filter(v -> v >= 0).toArray());
            long avgResp = avg(calls.stream().mapToLong(c -> c.responseContentLength).filter(v -> v >= 0).toArray());

            var m = new LinkedHashMap<String, Document>();
            put(m, "operation", entry.getKey());
            put(m, "callCount", count);
            put(m, "errorCount", errors);
            put(m, "p50DurationMillis", p50);
            put(m, "maxDurationMillis", max);
            m.put("avgAttempts", Document.of(avgAttempts));
            put(m, "avgRequestBytes", avgReq);
            put(m, "avgResponseBytes", avgResp);
            out.add(Document.of(m));
        }
        return Map.of("telemetry", Document.of(out));
    }

    // ---- Inject + rewrite ----

    private Map<String, Document> injectFault(Document input) {
        var rule = new InspectorState.Rule();
        rule.kind = InspectorState.Rule.Kind.FAULT;
        rule.operation = str(input, "operation");
        rule.remainingHits = intOr(input, "maxHits", 1);
        rule.httpStatus = intOrNull(input, "httpStatus");
        rule.throwable = str(input, "throwable");
        var latency = lng(input, "latencyMillis");
        rule.latencyMillis = latency == null ? 0 : latency;
        var id = state.addRule(rule);
        return Map.of("ruleId", Document.of(id));
    }

    private Map<String, Document> rewriteRequest(Document input) {
        var rule = new InspectorState.Rule();
        rule.kind = InspectorState.Rule.Kind.REWRITE;
        rule.operation = str(input, "operation");
        rule.remainingHits = intOr(input, "maxHits", 1);
        rule.endpoint = str(input, "endpoint");
        rule.setHeaders = stringMap(input, "setHeaders");
        var id = state.addRule(rule);
        return Map.of("ruleId", Document.of(id));
    }

    private Map<String, Document> listRules() {
        var out = new ArrayList<Document>();
        for (var r : state.listRules()) {
            var m = new LinkedHashMap<String, Document>();
            put(m, "ruleId", r.ruleId);
            put(m, "kind", r.kind.name());
            put(m, "operation", r.operation);
            put(m, "remainingHits", r.remainingHits);
            put(m, "detail", r.detail());
            out.add(Document.of(m));
        }
        return Map.of("rules", Document.of(out));
    }

    private Map<String, Document> clearRule(Document input) {
        int removed = state.clearRule(str(input, "ruleId"));
        return Map.of("removed", Document.of(removed));
    }

    // ---- Breakpoints ----

    private Map<String, Document> setBreakpoint(Document input) {
        var bp = new InspectorState.Breakpoint();
        bp.operation = str(input, "operation");
        bp.phase = reqStr(input, "phase");
        bp.remainingHits = intOr(input, "maxHits", 1);
        var timeout = lng(input, "timeoutMillis");
        if (timeout != null) {
            bp.timeoutMillis = timeout;
        }
        var id = state.addBreakpoint(bp);
        return Map.of("breakpointId", Document.of(id));
    }

    private Map<String, Document> listPausedFrames() {
        var out = new ArrayList<Document>();
        for (var f : state.listPausedFrames()) {
            var m = new LinkedHashMap<String, Document>();
            put(m, "frameId", f.frameId);
            put(m, "operation", f.operation);
            put(m, "phase", f.phase);
            put(m, "parkedMillis", System.currentTimeMillis() - f.parkedAtMillis);
            out.add(Document.of(m));
        }
        return Map.of("frames", Document.of(out));
    }

    private Map<String, Document> getPausedFrame(Document input) {
        var f = state.getPausedFrame(reqStr(input, "frameId"));
        if (f == null) {
            return Map.of();
        }
        var m = new LinkedHashMap<String, Document>();
        put(m, "frameId", f.frameId);
        put(m, "operation", f.operation);
        put(m, "service", f.service);
        put(m, "phase", f.phase);
        put(m, "requestMethod", f.requestMethod);
        put(m, "requestUri", f.requestUri);
        putHeaders(m, "requestHeaders", f.requestHeaders);
        return Map.of("frame", Document.of(m));
    }

    private Map<String, Document> resume(Document input) {
        var patch = new InspectorState.RequestPatch(str(input, "endpoint"), stringMap(input, "setHeaders"));
        boolean resumed = state.resume(reqStr(input, "frameId"), patch);
        return Map.of("resumed", Document.of(resumed));
    }

    private Map<String, Document> resumeAll() {
        return Map.of("resumed", Document.of(state.resumeAll()));
    }

    private Map<String, Document> listBreakpoints() {
        var out = new ArrayList<Document>();
        for (var bp : state.listBreakpoints()) {
            var m = new LinkedHashMap<String, Document>();
            put(m, "breakpointId", bp.breakpointId);
            put(m, "operation", bp.operation);
            put(m, "phase", bp.phase);
            put(m, "remainingHits", bp.remainingHits);
            put(m, "timeoutMillis", bp.timeoutMillis);
            out.add(Document.of(m));
        }
        return Map.of("breakpoints", Document.of(out));
    }

    private Map<String, Document> removeBreakpoint(Document input) {
        boolean removed = state.removeBreakpoint(reqStr(input, "breakpointId"));
        return Map.of("removed", Document.of(removed));
    }

    // ---- Shared shaping ----

    private static Document callSummary(InspectorState.CallRecord c) {
        var m = new LinkedHashMap<String, Document>();
        put(m, "callId", c.callId);
        put(m, "operation", c.operation);
        put(m, "service", c.service);
        put(m, "status", c.status);
        put(m, "errorType", c.errorType);
        put(m, "attempts", c.attempts.get());
        putIfPositive(m, "durationMillis", c.durationMillis);
        return Document.of(m);
    }

    // ---- Document read helpers ----

    private static String str(Document input, String member) {
        if (input == null) {
            return null;
        }
        var v = input.getMember(member);
        return v == null ? null : v.asString();
    }

    private static String reqStr(Document input, String member) {
        var v = str(input, member);
        if (v == null) {
            throw new IllegalArgumentException("Missing required member: " + member);
        }
        return v;
    }

    private static boolean boolOrFalse(Document input, String member) {
        if (input == null) {
            return false;
        }
        var v = input.getMember(member);
        return v != null && v.asBoolean();
    }

    private static Long lng(Document input, String member) {
        if (input == null) {
            return null;
        }
        var v = input.getMember(member);
        return v == null ? null : v.asNumber().longValue();
    }

    private static long reqLng(Document input, String member) {
        var v = lng(input, member);
        if (v == null) {
            throw new IllegalArgumentException("Missing required member: " + member);
        }
        return v;
    }

    private static Integer intOrNull(Document input, String member) {
        var v = lng(input, member);
        return v == null ? null : v.intValue();
    }

    private static int intOr(Document input, String member, int fallback) {
        var v = intOrNull(input, member);
        return v == null ? fallback : v;
    }

    private static Map<String, String> stringMap(Document input, String member) {
        if (input == null) {
            return null;
        }
        var v = input.getMember(member);
        if (v == null) {
            return null;
        }
        var out = new LinkedHashMap<String, String>();
        for (var e : v.asStringMap().entrySet()) {
            out.put(e.getKey(), e.getValue().asString());
        }
        return out;
    }

    // ---- Document write helpers (omit nulls) ----

    private static void put(Map<String, Document> m, String key, String value) {
        if (value != null) {
            m.put(key, Document.of(value));
        }
    }

    private static void put(Map<String, Document> m, String key, long value) {
        m.put(key, Document.of(value));
    }

    private static void put(Map<String, Document> m, String key, int value) {
        m.put(key, Document.of(value));
    }

    private static void putIfPositive(Map<String, Document> m, String key, long value) {
        if (value >= 0) {
            m.put(key, Document.of(value));
        }
    }

    private static void putHeaders(Map<String, Document> m, String key, Map<String, String> headers) {
        if (headers != null && !headers.isEmpty()) {
            var doc = new LinkedHashMap<String, Document>();
            headers.forEach((k, v) -> doc.put(k, Document.of(v)));
            m.put(key, Document.of(doc));
        }
    }

    private static void putStrings(Map<String, Document> m, String key, List<String> values) {
        if (values != null && !values.isEmpty()) {
            var list = new ArrayList<Document>();
            values.forEach(v -> list.add(Document.of(v)));
            m.put(key, Document.of(list));
        }
    }

    private static int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        return switch (level.toUpperCase()) {
            case "TRACE" -> 0;
            case "DEBUG" -> 1;
            case "INFO" -> 2;
            case "WARN" -> 3;
            case "ERROR", "FATAL" -> 4;
            default -> 0;
        };
    }

    private static long avg(long[] values) {
        if (values.length == 0) {
            return -1;
        }
        long sum = 0;
        for (long v : values) {
            sum += v;
        }
        return sum / values.length;
    }
}
