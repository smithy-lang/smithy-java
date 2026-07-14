# A/B comparison: inspector MCP vs. cold-repo baseline

Same underlying goal, two arms. Fill in one row set per run. Keep sessions fresh and the model/commit
identical across arms.

- **Arm A (MCP):** `AGENT_TASK.md` with `demo/mcp.json` attached.
- **Arm B (baseline):** `BASELINE_TASK.md`, no MCP config.

## Run metadata

| Field | Arm A (MCP) | Arm B (baseline) |
|---|---|---|
| Date | 2026-07-14 | 2026-07-14 |
| Model | claude-opus-4-8 (1m) | claude-opus-4-8 (1m) |
| Commit (`git rev-parse --short HEAD`) | b78025bf4 | b78025bf4 |
| Baseline uses pre-module checkout? (B only) | n/a | no (current checkout; wrote a throwaway probe test, did not use the inspector) |

## Per-question scoring

Score each of the 5 task questions: **Correct?** (Y/N), **Evidence-backed?** (observed vs. guessed),
**Turns/tool-calls to answer**.

### Arm A (MCP)

| # | Question | Correct? | Evidence-backed? | Turns/calls |
|---|---|---|---|---|
| 1 | Wire method/status + serialize vs. deserialize timing | Y | Y (GetCall phaseTimings: POST/200, serialize 4ms, deserialize 1ms) | 2 calls |
| 2 | Transmit attempts for 429→200 | Y | Y (ListCalls/GetCall: attempts=2) | 0 extra (same calls) |
| 3 | SDK internal log lines + levels | Y | Y (QueryLogs: 2 TRACE lines from ClientPipeline, quoted verbatim) | 1 call |
| 4 | Force a failure independent of backend | Y | Y (InjectFault→ok call→InspectorFaultException; faultInjected phase, no transmit; ListRules empty after) | 5 calls |
| 5 | Debugging-note summary | Y | Y (synthesized from tool output only) | 0 extra |

### Arm B (baseline)

| # | Question | Correct? | Evidence-backed? | Turns/calls |
|---|---|---|---|---|
| 1 | Wire method/status + serialize vs. deserialize timing | Y | Y (probe run: `wireMethod=POST`, `statuses=[200]`; `serializeMs=20`, `deserializeMs=13` — cold-JIT first call, sub-ms on warm runs) | shared: 1 file write + 2 gradle runs (1 to fix a compile error) after ~8 source-reading calls |
| 2 | Transmit attempts for 429→200 | Y | Y (same probe: `statuses=[429, 200]`, `attempts=2` from `readBeforeAttempt` counter) | 0 extra (same probe run) |
| 3 | SDK internal log lines + levels | Y | Y (captured verbatim: `ClientPipeline` `TRACE "Deserializing response with {} for {}:{}"` — surfaces as JUL `FINEST`; also `ClientConfig` `DEBUG "Applying plugin to ClientBuilder"` as `FINE`) | 0 extra (same probe; +1 grep of ClientPipeline to confirm source levels) |
| 4 | Force a failure independent of backend | Y | Y (probe primed backend with 200 but threw in `modifyBeforeTransmit`: `outcome=error`, `IllegalStateException BASELINE-PROBE-FORCED-FAILURE`, `statusesFromBackend=[]` → backend never hit) | 0 extra (same probe run) |
| 5 | Debugging-note summary | Y | Y (synthesized from probe stdout only) | 0 extra |

## Aggregate axes (the ones from the eval thread)

| Axis | Arm A (MCP) | Arm B (baseline) |
|---|---|---|
| Total conversation turns | 6 assistant turns | ~9 assistant turns |
| Total tool calls | 15 (14 MCP inspector + 1 git) | ~16 (11 Read/Bash source exploration, 1 Write probe, 1 Write+2 Edit fixes, 2 gradle test runs) |
| Approx. tokens (if visible) | n/a | n/a (higher than Arm A: large source files + full gradle logs read into context) |
| # questions correct (of 5) | 5 | 5 |
| # questions evidence-backed (of 5) | 5 | 5 |
| Wrote throwaway code? (files created) | No | Yes — 1 file: `src/test/.../BaselineProbeTest.java` (~300 lines mirroring `SprocketWorkload` + a hand-written observing interceptor and JUL log capture) |
| Read source files? (how many) | 0 (only the 3 demo/*.md task files) | ~7 (SprocketWorkload, MockPlugin, InspectorInterceptor, ClientPipeline, ClientInterceptor, ClientConfig, ClientPlugin) to learn the client wiring + hook API |
| Got stuck / needed a hint? (where) | No | No hint needed, but 1 self-inflicted compile error (`formatMessage` is on `Formatter`, not `Handler`) cost an extra edit + gradle run |
| Hallucinated an answer? (which #) | None | None (every number came from the probe's stdout) |

## Notes / sharp edges observed

- Arm A: Every answer came straight from tool output, no code or source reading. Sharp edges: (a) the
  fault-injected call was reported by ListCalls/GetCall as `status: in-progress` (never finalized to
  `error`) even though MakeSprocketCall returned `outcome: error` and phaseTimings showed
  `faultInjected` with no transmit — so the failure is confirmable from the call result + phase data,
  but the recorded call status itself is misleading. (b) QueryTelemetry `errorCount` stayed 0 and
  `avgRequestBytes` is -1 (request bytes not captured), so telemetry alone wouldn't surface the
  injected failure. (c) retry-call phase timings all read 0 ms (sub-ms), so timing detail is best read
  from the `ok` call.
- Arm B: To answer *anything* I first had to reconstruct how the Sprockets client is wired. There
  is no ready-made runnable entry point outside the inspector, so the fastest honest path was to
  mirror `SprocketWorkload`'s client build (REST-JSON + SigV4 + MockPlugin + zero-backoff retry) in a
  throwaway JUnit test and attach my own observing `ClientInterceptor`. Sharp edges: (a) attempt
  count and phase timings are only observable by implementing the interceptor hooks
  (`readBeforeAttempt`, `readAfterSerialization`, `readAfterDeserialization`) myself — nothing exposes
  them otherwise. (b) The SDK's internal logs go through `InternalLogger` → SLF4J → JUL
  (`slf4j-jdk14`), so capturing them meant setting the root logger to `Level.ALL` and attaching a JUL
  `Handler`; the useful `ClientPipeline` line logs at `TRACE`, which surfaces as JUL `FINEST`.
  (c) Forcing a backend-independent failure was easy *once the interceptor existed* — throw in
  `modifyBeforeTransmit` and confirm the primed 200 was never consumed (`statusesFromBackend=[]`).
  (d) First-call timings are inflated by cold JIT/classloading (serialize 20ms / deserialize 13ms on
  the first call); they drop to sub-ms on warm calls, so absolute numbers are noisy. (e) One
  self-inflicted compile error (`formatMessage` lives on `Formatter`, not `Handler`) cost an extra
  build cycle. Net: same 5/5 correct-and-evidence-backed result as Arm A, but it required reading ~7
  source files and writing ~300 lines of throwaway code instead of ~15 tool calls and zero code.

### Q5 debugging note (Arm B, from the probe run)

> `GetSprocket` is a REST-JSON operation: the client serializes `{id}` and puts it on the wire as
> `POST http://localhost/s` (SigV4-signed). On an `ok` call the backend returns `200` with
> `{"id":"..."}`, one transmit attempt, and the response deserializes back to the output shape. On a
> `retry` call the first attempt gets `429` (retryable), the client re-enters the attempt loop and
> the second attempt gets `200` — **2 transmit attempts for one logical call**, both to the same
> `POST /s`. Internally the SDK's `ClientPipeline` emits a `TRACE`
> `"Deserializing response with {} for {}:{}"` per attempt (visible as JUL `FINEST`), and `ClientConfig`
> emits `DEBUG "Applying plugin to ClientBuilder"` (`FINE`) at build time. A failure injected in the
> client (throwing in `modifyBeforeTransmit`) surfaces to the caller as the thrown exception with the
> backend never contacted — proving the failure was client-side, not from the mock server.

## One-line takeaway

> With the inspector MCP, all 5 questions were answered correctly and evidence-backed in ~15 tool
> calls with zero source reading or throwaway code — the wire-level, retry, and log facts came
> directly from the running SDK.
>
> The baseline reached the same 5/5 correct-and-evidence-backed result, but only by reading ~7
> source files to reverse-engineer the client wiring and writing ~300 lines of throwaway test code
> (a hand-rolled observing interceptor + JUL log capture) — the effort the inspector's tools
> eliminate. Same answers, roughly 3× the reading and a build-fix loop the MCP arm never incurred.
