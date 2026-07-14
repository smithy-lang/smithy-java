/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package software.amazon.smithy.java.client.inspector.mcp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide shared state for the inspector: the recorded-call ring buffer, captured logs,
 * armed inject/rewrite rules, armed breakpoints, and parked frames.
 *
 * <p>A single instance is shared by the {@link InspectorInterceptor} (writer, on client threads)
 * and the {@link InspectorService} MCP tools (reader/controller, on the MCP server thread). All
 * mutable collections are concurrent, so the {@code resume} tool can wake a parked client thread
 * by counting down a latch it finds in {@link #pausedFrames} without any cross-process IPC.
 *
 * <p>This is a dev/eval instrument. It retains request/response metadata in memory and is not
 * intended for production use.
 */
public final class InspectorState {

    /** Default number of recent calls retained in the ring buffer. */
    public static final int DEFAULT_MAX_CALLS = 500;

    private final int maxCalls;
    private final AtomicLong callIdSeq = new AtomicLong();
    private final AtomicLong ruleIdSeq = new AtomicLong();
    private final AtomicLong frameIdSeq = new AtomicLong();

    // Most-recent-last; trimmed to maxCalls. Guarded by its own monitor.
    private final Deque<CallRecord> calls = new ArrayDeque<>();
    private static final int MAX_LOGS = 2000;
    private final Deque<LogEntry> logs = new ArrayDeque<>();

    private final Map<String, Rule> rules = new ConcurrentHashMap<>();
    private final Map<String, Breakpoint> breakpoints = new ConcurrentHashMap<>();
    private final Map<String, PausedFrame> pausedFrames = new ConcurrentHashMap<>();

    private volatile ConfigRecord lastConfig;

    // The call id currently executing on this thread, so the log handler can correlate log lines to
    // the recorded call that emitted them.
    private final ThreadLocal<Long> currentCallId = new ThreadLocal<>();

    public InspectorState() {
        this(DEFAULT_MAX_CALLS);
    }

    public InspectorState(int maxCalls) {
        this.maxCalls = maxCalls;
    }

    // ---- Calls ----

    long nextCallId() {
        return callIdSeq.incrementAndGet();
    }

    void addCall(CallRecord record) {
        synchronized (calls) {
            calls.addLast(record);
            while (calls.size() > maxCalls) {
                calls.removeFirst();
            }
        }
    }

    public List<CallRecord> listCalls() {
        synchronized (calls) {
            return new ArrayList<>(calls);
        }
    }

    public CallRecord getCall(long id) {
        synchronized (calls) {
            for (CallRecord c : calls) {
                if (c.callId == id) {
                    return c;
                }
            }
        }
        return null;
    }

    void setLastConfig(ConfigRecord config) {
        this.lastConfig = config;
    }

    public ConfigRecord lastConfig() {
        return lastConfig;
    }

    void setCurrentCallId(Long id) {
        if (id == null) {
            currentCallId.remove();
        } else {
            currentCallId.set(id);
        }
    }

    /** The call id executing on the calling thread, or null. Used to correlate logs to calls. */
    public Long currentCallId() {
        return currentCallId.get();
    }

    // ---- Logs ----

    public void addLog(LogEntry entry) {
        synchronized (logs) {
            logs.addLast(entry);
            while (logs.size() > MAX_LOGS) {
                logs.removeFirst();
            }
        }
    }

    public List<LogEntry> listLogs() {
        synchronized (logs) {
            return new ArrayList<>(logs);
        }
    }

    // ---- Rules ----

    String addRule(Rule rule) {
        var id = "rule-" + ruleIdSeq.incrementAndGet();
        rule.ruleId = id;
        rules.put(id, rule);
        return id;
    }

    public List<Rule> listRules() {
        return new ArrayList<>(rules.values());
    }

    public int clearRule(String ruleId) {
        if (ruleId == null) {
            int n = rules.size();
            rules.clear();
            return n;
        }
        return rules.remove(ruleId) != null ? 1 : 0;
    }

    /**
     * Find the first armed rule of the given kind matching the operation, decrementing and expiring
     * it if its hit budget is exhausted. Returns null if none match.
     */
    Rule consumeRule(Rule.Kind kind, String operation) {
        for (Rule r : rules.values()) {
            if (r.kind == kind && r.matches(operation) && r.tryConsume()) {
                if (r.remainingHits <= 0) {
                    rules.remove(r.ruleId);
                }
                return r;
            }
        }
        return null;
    }

    // ---- Breakpoints ----

    String addBreakpoint(Breakpoint bp) {
        var id = "bp-" + ruleIdSeq.incrementAndGet();
        bp.breakpointId = id;
        breakpoints.put(id, bp);
        return id;
    }

    public List<Breakpoint> listBreakpoints() {
        return new ArrayList<>(breakpoints.values());
    }

    public boolean removeBreakpoint(String id) {
        return breakpoints.remove(id) != null;
    }

    Breakpoint matchBreakpoint(String phase, String operation) {
        for (Breakpoint bp : breakpoints.values()) {
            if (bp.phase.equals(phase) && bp.matches(operation) && bp.tryConsume()) {
                if (bp.remainingHits <= 0) {
                    breakpoints.remove(bp.breakpointId);
                }
                return bp;
            }
        }
        return null;
    }

    // ---- Paused frames ----

    String parkFrame(PausedFrame frame) {
        var id = "frame-" + frameIdSeq.incrementAndGet();
        frame.frameId = id;
        pausedFrames.put(id, frame);
        return id;
    }

    void unparkFrame(String id) {
        pausedFrames.remove(id);
    }

    public List<PausedFrame> listPausedFrames() {
        return new ArrayList<>(pausedFrames.values());
    }

    public PausedFrame getPausedFrame(String id) {
        return pausedFrames.get(id);
    }

    /** Resume one frame; returns true if it was parked. The client thread applies {@code frame.patch}. */
    public boolean resume(String id, RequestPatch patch) {
        var frame = pausedFrames.remove(id);
        if (frame == null) {
            return false;
        }
        frame.patch = patch;
        frame.latch.countDown();
        return true;
    }

    public int resumeAll() {
        int n = 0;
        for (var frame : new ArrayList<>(pausedFrames.values())) {
            if (pausedFrames.remove(frame.frameId) != null) {
                frame.latch.countDown();
                n++;
            }
        }
        return n;
    }

    // ============================================================
    // Records
    // ============================================================

    /** A recorded call. Fields are written across lifecycle hooks then frozen when the call ends. */
    public static final class CallRecord {
        public long callId;
        public String operation;
        public String service;
        public volatile String status = "in-progress";
        public volatile String errorType;
        public volatile String errorMessage;
        public final AtomicInteger attempts =
                new AtomicInteger();
        public long startNanos;
        public volatile long durationMillis = -1;
        // Transient nano markers for in-flight phase timing (written/read on the client thread).
        public long serializeStartNanos;
        public long signingStartNanos;
        public long deserializeStartNanos;
        public long attemptStartNanos;
        public String endpoint;
        public String requestMethod;
        public String requestUri;
        public Map<String, String> requestHeaders;
        public long requestContentLength = -1;
        public int responseStatus = -1;
        public Map<String, String> responseHeaders;
        public long responseContentLength = -1;
        public final Map<String, Long> phaseTimings = new ConcurrentHashMap<>();
    }

    /** A resolved-config snapshot. */
    public static final class ConfigRecord {
        public String service;
        public String protocol;
        public String transport;
        public String endpointResolver;
        public List<String> authSchemes;
        public List<String> identityResolvers;
        public String retryStrategy;
    }

    /** A captured log record. */
    public static final class LogEntry {
        public final String level;
        public final String logger;
        public final String message;
        public final long epochMillis;
        public final String thrown;
        public final Long callId;

        public LogEntry(String level, String logger, String message, long epochMillis, String thrown, Long callId) {
            this.level = level;
            this.logger = logger;
            this.message = message;
            this.epochMillis = epochMillis;
            this.thrown = thrown;
            this.callId = callId;
        }
    }

    /** An armed inject/rewrite rule. */
    public static final class Rule {
        public enum Kind {
            FAULT,
            REWRITE
        }

        public String ruleId;
        public Kind kind;
        public String operation;
        public int remainingHits = 1;
        // Fault
        public Integer httpStatus;
        public String throwable;
        public long latencyMillis;
        // Rewrite
        public String endpoint;
        public Map<String, String> setHeaders;

        boolean matches(String op) {
            return operation == null || operation.equals(op);
        }

        synchronized boolean tryConsume() {
            if (remainingHits <= 0) {
                return false;
            }
            remainingHits--;
            return true;
        }

        public String detail() {
            if (kind == Kind.FAULT) {
                return "status=" + httpStatus + " throw=" + throwable + " latencyMs=" + latencyMillis;
            }
            return "endpoint=" + endpoint + " headers=" + setHeaders;
        }
    }

    /** An armed breakpoint. */
    public static final class Breakpoint {
        public String breakpointId;
        public String operation;
        public String phase;
        public int remainingHits = 1;
        public long timeoutMillis = 30_000;

        boolean matches(String op) {
            return operation == null || operation.equals(op);
        }

        synchronized boolean tryConsume() {
            if (remainingHits <= 0) {
                return false;
            }
            remainingHits--;
            return true;
        }
    }

    /** A call parked at a breakpoint, waiting on {@link #latch}. */
    public static final class PausedFrame {
        public String frameId;
        public String operation;
        public String service;
        public String phase;
        public String requestMethod;
        public String requestUri;
        public Map<String, String> requestHeaders;
        public long parkedAtMillis;
        public final CountDownLatch latch = new CountDownLatch(1);
        public volatile RequestPatch patch;
    }

    /** A request rewrite supplied by a rule or a resume. */
    public static final class RequestPatch {
        public final String endpoint;
        public final Map<String, String> setHeaders;

        public RequestPatch(String endpoint, Map<String, String> setHeaders) {
            this.endpoint = endpoint;
            this.setHeaders = setHeaders;
        }
    }
}
