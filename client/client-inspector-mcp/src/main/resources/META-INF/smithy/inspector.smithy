$version: "2.0"

namespace software.amazon.smithy.java.client.inspector

/// A control-plane service that a coding agent drives over MCP to observe, instrument,
/// and control a smithy-java SDK client running in the same process. Dev/eval use only.
service SdkInspector {
    version: "2024-01-01"
    operations: [
        // ---- Tier 1: Observe (non-blocking recorder) ----
        ListCalls
        GetCall
        GetConfig
        // ---- Logging + telemetry (queryable) ----
        QueryLogs
        QueryTelemetry
        // ---- Tier 2: Inject + rewrite (declarative) ----
        InjectFault
        RewriteRequest
        ListRules
        ClearRule
        // ---- Tier 3: Breakpoints (blocking pause/resume) ----
        SetBreakpoint
        ListPausedFrames
        GetPausedFrame
        Resume
        ResumeAll
        ListBreakpoints
        RemoveBreakpoint
    ]
}

// ============================================================
// Tier 1: Observe
// ============================================================

/// List recorded calls, most recent first. Read-only.
@readonly
operation ListCalls {
    input := {
        /// Only return calls for this operation name.
        operation: String
        /// Only return calls that ended in an error.
        errorOnly: Boolean
        /// Only return calls with an id greater than this (for polling).
        sinceId: Long
        /// Max number of calls to return (default 50).
        limit: Integer
    }
    output := {
        calls: CallSummaryList
    }
}

/// Get the full recorded frame for a single call: resolved endpoint, auth scheme,
/// wire request/response, per-phase timings, and error. Read-only.
@readonly
operation GetCall {
    input := {
        @required
        callId: Long
    }
    output := {
        call: CallDetail
    }
}

/// Get the resolved client configuration (protocol, transport, endpoint resolver,
/// auth schemes, retry strategy). Read-only.
@readonly
operation GetConfig {
    input := {
        /// If set, return the config resolved for this specific call; otherwise the last seen config.
        callId: Long
    }
    output := {
        config: ConfigSnapshot
    }
}

// ============================================================
// Logging + telemetry
// ============================================================

/// Query captured log records (from java.util.logging), most recent first. Read-only.
@readonly
operation QueryLogs {
    input := {
        /// Minimum level: TRACE, DEBUG, INFO, WARN, ERROR.
        minLevel: String
        /// Substring the message must contain.
        contains: String
        /// Correlate to a specific recorded call.
        callId: Long
        limit: Integer
    }
    output := {
        logs: LogRecordList
    }
}

/// Query captured telemetry events (per-phase timings, payload sizes, attempts, errors)
/// aggregated across recorded calls. Read-only.
@readonly
operation QueryTelemetry {
    input := {
        /// Only aggregate telemetry for this operation.
        operation: String
    }
    output := {
        telemetry: TelemetrySummaryList
    }
}

// ============================================================
// Tier 2: Inject + rewrite
// ============================================================

/// Arm a fault to be injected on the next matching calls. Returns the rule id.
@idempotent
operation InjectFault {
    input := {
        /// Match calls for this operation name (null = all operations).
        operation: String
        /// Number of matching calls to affect before the rule expires (default 1).
        maxHits: Integer
        /// HTTP status to inject instead of transmitting, e.g. 500.
        httpStatus: Integer
        /// Fully-qualified exception class to throw instead, e.g. java.io.IOException.
        throwable: String
        /// Additional latency to add before transmit, in milliseconds.
        latencyMillis: Long
    }
    output := {
        ruleId: String
    }
}

/// Arm a request rewrite applied to matching requests before transmit. Returns the rule id.
@idempotent
operation RewriteRequest {
    input := {
        operation: String
        maxHits: Integer
        /// Override the request endpoint/URI (e.g. point at a local mock).
        endpoint: String
        /// Headers to set on the outgoing request.
        setHeaders: HeaderMap
    }
    output := {
        ruleId: String
    }
}

/// List currently armed inject/rewrite rules. Read-only.
@readonly
operation ListRules {
    output := {
        rules: RuleList
    }
}

/// Remove an armed rule by id (or all rules if no id is given).
@idempotent
operation ClearRule {
    input := {
        ruleId: String
    }
    output := {
        removed: Integer
    }
}

// ============================================================
// Tier 3: Breakpoints
// ============================================================

/// Arm a breakpoint that pauses matching calls at a lifecycle phase until resumed
/// (or until it times out and fails open). Returns the breakpoint id.
@idempotent
operation SetBreakpoint {
    input := {
        operation: String
        /// Lifecycle phase to pause at: beforeTransmit, beforeDeserialize, beforeSigning.
        @required
        phase: String
        maxHits: Integer
        /// How long a paused call waits before failing open, in milliseconds (default 30000).
        timeoutMillis: Long
    }
    output := {
        breakpointId: String
    }
}

/// List calls currently parked at a breakpoint. Read-only.
@readonly
operation ListPausedFrames {
    output := {
        frames: PausedFrameList
    }
}

/// Get the full captured state of a paused frame. Read-only.
@readonly
operation GetPausedFrame {
    input := {
        @required
        frameId: String
    }
    output := {
        frame: PausedFrameDetail
    }
}

/// Resume a paused frame, optionally applying a request rewrite before it continues.
@idempotent
operation Resume {
    input := {
        @required
        frameId: String
        endpoint: String
        setHeaders: HeaderMap
    }
    output := {
        resumed: Boolean
    }
}

/// Resume all paused frames (panic button / eval teardown).
@idempotent
operation ResumeAll {
    output := {
        resumed: Integer
    }
}

/// List armed breakpoints. Read-only.
@readonly
operation ListBreakpoints {
    output := {
        breakpoints: BreakpointList
    }
}

/// Remove an armed breakpoint (does not resume already-parked frames).
@idempotent
operation RemoveBreakpoint {
    input := {
        @required
        breakpointId: String
    }
    output := {
        removed: Boolean
    }
}

// ============================================================
// Shapes
// ============================================================

list CallSummaryList {
    member: CallSummary
}

structure CallSummary {
    callId: Long
    operation: String
    service: String
    status: String
    errorType: String
    attempts: Integer
    durationMillis: Long
}

structure CallDetail {
    callId: Long
    operation: String
    service: String
    status: String
    errorType: String
    errorMessage: String
    attempts: Integer
    durationMillis: Long
    endpoint: String
    authScheme: String
    requestMethod: String
    requestUri: String
    requestHeaders: HeaderMap
    requestContentLength: Long
    responseStatus: Integer
    responseHeaders: HeaderMap
    responseContentLength: Long
    phaseTimings: PhaseTimingMap
}

map PhaseTimingMap {
    key: String
    value: Long
}

structure ConfigSnapshot {
    service: String
    protocol: String
    transport: String
    endpointResolver: String
    authSchemes: StringList
    identityResolvers: StringList
    retryStrategy: String
}

list LogRecordList {
    member: LogRecord
}

structure LogRecord {
    level: String
    logger: String
    message: String
    epochMillis: Long
    thrown: String
    callId: Long
}

list TelemetrySummaryList {
    member: TelemetrySummary
}

structure TelemetrySummary {
    operation: String
    callCount: Long
    errorCount: Long
    p50DurationMillis: Long
    maxDurationMillis: Long
    avgAttempts: Double
    avgRequestBytes: Long
    avgResponseBytes: Long
}

list RuleList {
    member: RuleInfo
}

structure RuleInfo {
    ruleId: String
    kind: String
    operation: String
    remainingHits: Integer
    detail: String
}

list BreakpointList {
    member: BreakpointInfo
}

structure BreakpointInfo {
    breakpointId: String
    operation: String
    phase: String
    remainingHits: Integer
    timeoutMillis: Long
}

list PausedFrameList {
    member: PausedFrameSummary
}

structure PausedFrameSummary {
    frameId: String
    operation: String
    phase: String
    parkedMillis: Long
}

structure PausedFrameDetail {
    frameId: String
    operation: String
    service: String
    phase: String
    requestMethod: String
    requestUri: String
    requestHeaders: HeaderMap
}

map HeaderMap {
    key: String
    value: String
}

list StringList {
    member: String
}
