## client-inspector-mcp

> [!WARNING]
> Dev/eval-only. This module instruments a running smithy-java client and exposes control over it
> (fault injection, request rewriting, breakpoints) as MCP tools. It is not for production use.

A prototype that turns a running smithy-java SDK client into a **queryable, controllable surface an
agent can drive over MCP**. It has two halves:

- An opt-in `ClientPlugin` (`InspectorPlugin`) whose interceptor records every call — resolved
  config, wire request/response, per-phase timings, attempts, errors — into a shared
  `InspectorState`, and applies armed fault/rewrite/breakpoint rules to the live request pipeline.
- A hand-built MCP `Service` (`InspectorService`) whose operations read and control that state, so
  each becomes an MCP tool. See `src/main/resources/META-INF/smithy/inspector.smithy` for the tool
  contract.

Logs (via a JUL bridge) and telemetry (per-phase timings aggregated across calls) are captured and
made queryable too.

### Try it with a real agent

The `demo/` directory contains a self-contained agent demo. Before the stdio MCP server
(`InspectorDemoServer`) starts serving, a `DemoTrafficDriver` makes a fixed, agent-invisible batch
of real SDK calls (a success, a retried 429, a persistent 500) — standing in for the developer's own
app. The server then exposes only the **inspector tools**. The agent's job is to characterize
traffic it did not author; there is deliberately no traffic-generation tool, so it cannot script its
own answer.

```bash
# Build the runnable server (generates start scripts under build/install/).
./gradlew :client:client-inspector-mcp:installDist
```

Point an MCP client at it. For Claude Code, from this module directory:

```bash
claude --mcp-config demo/mcp.json
```

(`demo/mcp.json` launches `build/install/client-inspector-mcp/bin/client-inspector-mcp` over stdio.)

Then give the agent the task in `demo/AGENT_TASK.md` — it will generate traffic, inspect the
recorded calls/telemetry/logs, inject a fault, and report what the SDK actually did on the wire.

### How it maps to production

- `InspectorPlugin` is **opt-in and off by default** (flag-gated: `smithy.inspector` /
  `SMITHY_INSPECTOR`), and deliberately not an auto-plugin, so it never silently instruments a
  production client. Ship it in a separate dev artifact.
- stdio is the default transport (one agent drives one process). For a shared/networked deployment,
  wire `InspectorService` into an HTTP MCP server; keep breakpoints stdio-only.

### Tests

- `InspectorServiceTest` — the control service exercised the way the MCP server dispatches.
- `InspectorEndToEndTest` — a real `DynamicClient` through a mock transport, inspected via the tools.
- `demo/../AgentLoopProtocolTest` — the full agent loop over the **real MCP JSON-RPC wire protocol**.
