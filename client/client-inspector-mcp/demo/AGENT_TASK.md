# Contrived agent task: characterize SDK traffic with the SDK Inspector

You have an MCP server (`sdk-inspector-demo`) attached to a running smithy-java SDK client for a
fake "Sprockets" service. Before you connected, the app made a batch of real `GetSprocket` calls —
you did **not** author them and cannot generate more. Your job is to figure out what happened, using
only the inspector tools. No source code reading required.

## Tools available

**Observe**
- `ListCalls(operation?, errorOnly?, sinceId?, limit?)` — recorded calls, newest first.
- `GetCall(callId)` — full detail: wire request/response, headers, status, per-phase timings, attempts.
- `GetConfig(callId?)` — resolved client config (protocol, transport, endpoint, auth schemes).
- `QueryTelemetry(operation?)` — aggregates: call/error counts, p50/max duration, avg attempts/bytes.
- `QueryLogs(minLevel?, contains?, callId?, limit?)` — the SDK's own internal logs, filterable and
  correlated to a call.

**Control**
- `InjectFault(operation?, httpStatus?, throwable?, latencyMillis?, maxHits?)` — make future matching
  calls fail (note: the demo traffic already ran, so this affects nothing unless you have a way to
  trigger more — use it to demonstrate the mechanism / inspect an armed rule).
- `RewriteRequest(...)`, `ListRules`, `ClearRule` — declarative request rewriting.
- `SetBreakpoint(phase, operation?, timeoutMillis?, maxHits?)`, `ListPausedFrames`, `GetPausedFrame`,
  `Resume`, `ResumeAll`, `ListBreakpoints`, `RemoveBreakpoint` — pause a call mid-flight, inspect it,
  and resume (optionally patching the request).

## Your task

Work only from what the tools report — do not assume what the traffic was.

1. Enumerate the recorded calls with `ListCalls`. How many were there, and what was the outcome of
   each?
2. For the successful call, use `GetCall` to report the HTTP method/status and the
   serialize/deserialize phase timings.
3. One call took more than one attempt. Find it, and report how many attempts it took and how it
   ended. Explain *why* it retried (what does the recorded data tell you?).
4. One call failed permanently. Find it via `ListCalls(errorOnly: true)`, and report its error type
   and how many attempts were made before giving up.
5. Using `QueryTelemetry` and `QueryLogs`, corroborate your findings (aggregate counts; quote one
   internal SDK log line).
6. Write a short debugging note: what did the SDK do on the wire across these calls, and what would
   you tell a developer seeing intermittent vs. persistent failures?
