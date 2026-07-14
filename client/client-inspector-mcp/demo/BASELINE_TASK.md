# Baseline agent task (no inspector, no MCP): understand a flaky SDK call

This is the control arm for comparing against `AGENT_TASK.md`. It states the **same underlying
goal** but names no tools, so the agent must figure out how to answer using only the repo — reading
source, writing a throwaway program, adding logging, etc.

## Context

This repo (`smithy-java`) is a Smithy SDK framework for Java. Consider a REST-JSON client for a
service `smithy.example#Sprockets` with a single operation `GetSprocket` (`POST /s`, input `{id}`,
output `{id}`), talking to a backend that can return `200`, a retryable `429`, or a `500`.

## Your task

Answer the following about what the SDK actually does at runtime — not by guessing from docs, but by
observing real behavior:

1. For a successful `GetSprocket` call: what HTTP method and status go on the wire, and roughly how
   long does request serialization vs. response deserialization take?
2. For a call where the backend returns `429` then `200`: how many transmit attempts does the client
   make for the single logical call?
3. What internal log lines does the SDK emit during a call, and at what levels?
4. Make a `GetSprocket` call fail on demand even though the backend would succeed, and confirm the
   failure came from your intervention rather than the backend.
5. Write a short debugging note summarizing what the SDK did on the wire.

Do whatever it takes with the repo as-is: read the client/interceptor source, write and run a small
program, add temporary logging or an interceptor, use a mock transport, etc.

## What to record (for comparison)

- Number of conversation turns / tool calls to reach each answer.
- Whether each answer is correct and evidence-backed.
- Where you got stuck or had to guess.
