# Runtime Codec Generation

Status: internal experiment, disabled by default

## Goals

Runtime codec generation specializes model access and wire operations for a
root schema while retaining the existing codecs as the semantic fallback. JSON
is the first complete backend. A small CBOR backend slice exercises the shared
planner, emitter, cache, publication, eviction, and unloading paths without
sharing JSON parsing or writing code.

The feature does not add a public API and is not selected by default.

## Assumptions

- Production class files target Java 21. The build may use a newer toolchain,
  but generated classes must load on Java 21.
- Generated model getters, builder setters, schema fields, and builder factory
  methods are public. A schema graph that does not expose compatible accessors
  is unsupported and uses the generic codec.
- Schema objects and generated model classes are immutable after publication.
- Protocol settings that affect wire behavior are part of cache identity.
- Event stream framing remains outside generated payload codecs. Event payloads
  can use generation only after the normal event-stream layer selects a payload
  codec.
- Validation and client error correction remain builder responsibilities.
  Generated deserialization invokes the same builder setters, error correction,
  and build sequence as the generic codec.

## Architecture

The internal code-generation package in `codec-commons` owns generation-time
infrastructure:

- schema graph traversal and recursion detection;
- model getter and builder-setter discovery;
- immutable generation plans;
- method-size estimates and split points;
- ASM class emission and hidden-class definition;
- cache identity and deterministic generated names;
- concurrent generation deduplication and atomic publication;
- failure caching, bounded eviction, diagnostics, and lifecycle counters.

Codec modules own backend implementations. A backend consumes the immutable
plan and emits bytecode that directly calls its own parser/writer primitives.
The backend object and the plan are not retained by, or passed through,
generated operations. There is no per-member backend dispatch and no runtime
plan interpreter.

Generated classes are hidden nestmates of a backend-owned lookup anchor. This
avoids a permanent generated-class namespace and permits unloading after cache
eviction. Cache keys hold model classes weakly through a `ClassValue` root
partition; entries hold generated instances strongly only until eviction.

## Classfile Backend

The implementation uses ASM 9.7.1, shaded into `codec-commons`.

The JDK ClassFile API is rejected because the production baseline is Java 21
and the API is not available there. Runtime `javac` is rejected because the
study measured 193-590 ms per root, it requires a full JDK, and source
compilation complicates isolation. A new classfile writer is rejected because
stack-map generation, verifier correctness, and ongoing classfile maintenance
would dominate this experiment. ASM adds a small shaded dependency, supports
Java 21 class files, produces ordinary bytecode, and has low generation
latency.

## Planning And Splitting

Plans classify every reachable member by Smithy type, wire name, Java getter,
builder setter, nullability, collection element/value type, timestamp format,
and recursive edge. Unsupported edges reject the complete root plan.

Writer methods split after eight members or an estimated 280 bytecodes,
whichever comes first. Reader dispatch uses hash buckets and splits at an
estimated 300 bytecodes. These conservative thresholds follow the study result
that unsplit 32- and 64-member methods regress after JIT inlining stops.
Backends may lower the thresholds but may not defer splitting decisions to the
generated runtime path.

## Feature Gates

- `smithy-java.runtime-codegen=json` enables generated JSON codecs.
- `smithy-java.runtime-codegen=cbor` enables the CBOR proof slice.
- A comma-separated value enables both.
- `smithy-java.aws-json-runtime-codegen=true` enables the AWS JSON protocol
  experiment and implies JSON generation only for AWS JSON codec instances.

All gates are read when a codec is constructed. Changing a property does not
mutate an existing codec.

## Fallback And Failure

Generation is all-or-nothing for a root schema and settings identity. Planning,
emission, class definition, or linkage failure publishes a cached failure.
Calls in progress and future calls use the existing generic codec. Failures do
not change payload semantics and are exposed only through internal diagnostics.

Malformed payloads, validation failures, and application exceptions from a
successfully generated codec are not generation failures and are never retried
through the generic codec.

## Cache And Lifecycle

Concurrent requests share one generation future. Publication is atomic and a
generated codec becomes visible only after construction succeeds. The default
cache is bounded to 256 roots per backend. Approximate LRU eviction removes
completed entries; in-flight entries are not evicted. Clearing the final entry
reference makes its hidden classes and class data eligible for unloading.

Diagnostics count requests, hits, generation successes and failures, fallback
uses, evictions, emitted classes, emitted bytes, and generation time. They do
not retain schemas, model classes, generated classes, or class loaders.

## Semantic Scope

The JSON backend targets structures, unions, lists, maps, primitives, blobs,
enums, timestamps, documents, recursive graphs, borrowed serialization,
detached serialization, and full-model deserialization. It embeds field-name
bytes and hashes and calls direct getters and builder setters.

Pretty printing and any schema edge whose generated semantics cannot exactly
match the current provider fall back. Unknown fields are skipped. Unknown union
members follow JSON settings. Null, required, missing-member, malformed input,
timestamp, document, and numeric behavior must match the generic codec.

The CBOR slice deliberately supports a smaller graph and is not an adoption
claim. It must still emit CBOR-specific bytecode and call CBOR-specific parser
and writer primitives.

## Adoption

Keep the implementation experimental unless all of these hold:

- at least 10% codec-only improvement on multiple medium/large workloads;
- at least 5% AWS JSON end-to-end improvement on multiple workloads;
- no repeatable JSON regression above 2% against the monolithic study
  prototype;
- no semantic regressions in protocol and codec tests;
- bounded class, metaspace, and code-cache growth;
- reliable concurrent publication, fallback, eviction, and unloading.
