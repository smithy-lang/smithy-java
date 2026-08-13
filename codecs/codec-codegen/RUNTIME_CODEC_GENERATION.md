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
- Generated union Java types can be sealed interfaces even when protocol
  projection schemas report them as structures. Planning recognizes the Java
  representation and normalizes acronym member names such as `S`, `NS`, and
  `NULL` to their generated record accessors and builder setters.
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
- cache identity and collision-free generated names;
- concurrent generation deduplication and atomic publication;
- failure caching, bounded eviction, diagnostics, and lifecycle counters.

Codec modules own backend implementations. A backend consumes the immutable
plan and emits bytecode that directly calls its own parser/writer primitives.
The backend object and the plan are not retained by, or passed through,
generated operations. There is no per-member backend dispatch and no runtime
plan interpreter.

Generated classes are hidden nestmates of a backend-owned lookup anchor. This
avoids a permanent generated-class namespace and permits unloading after cache
eviction. Each feature-gated provider keeps a bounded, allocation-free
steady-state publication map. The registry and publication map both cap
retained roots at 256; entries hold generated instances strongly until
eviction or explicit clearing.

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

The surrounding bounded caches necessarily retain those objects while an entry
is live. Diagnostics retain only failure class/message/location strings.

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

## Current Implementation

The JSON backend lowers direct structure getters and builder setters,
structures, generated sealed-interface unions, lists, maps, scalar primitives,
blobs, string enums, timestamps, documents, sparse aggregates, borrowed
output, detached output, full-model input, and an allocation-free
validation/token-sink entry.
Field names and encoded field tokens are class constants. Writers split at
eight members and wide reader dispatch splits into bounded hash helpers.

Unsupported graphs, including integer enums and generated models without
public direct access, fall back before a class is published. Pretty printing
also falls back. Event-stream framing is unchanged and only its JSON payload
codec can participate.

The CBOR proof lowers direct scalar structures only. Lists, maps, nested
structures, unions, enums, documents, and other unsupported CBOR graphs use
the existing codec.

## Short Performance Screen

The short screen ran on JDK 26.0.1, G1, fixed 1 GiB heap,
`AlwaysPreTouch`, one JMH thread, and CPU 2. It used two 2-second warmups and
three 3-second average-time measurements, so it is directional rather than an
adoption result. Every generated case executed a published generated class;
setup failures abort the benchmark.

| Workload | Surface | Generic ns/op | Generated ns/op | Change |
| --- | --- | ---: | ---: | ---: |
| GetItem M | codec deserialize | 4,984 | 4,366 | +12.4% |
| GetItem L | codec deserialize | 35,556 | 35,340 | +0.6% |
| GetItem M | protocol deserialize | 5,036 | 4,408 | +12.5% |
| GetItem L | protocol deserialize | 34,710 | 35,906 | -3.4% |
| PutItem mixed M | codec serialize | 3,718 | 3,977 | -6.9% |
| PutItem mixed M | protocol serialize | 3,925 | 4,462 | -13.7% |

Generated deserialization allocated 1,248 B/op more for GetItem M and
7,527 B/op more for GetItem L. PutItem allocated 64 B/op more codec-only and
64 B/op less end to end. Results are in
`build/perf-study/runtime-codegen-production/final-short-screen-generated.json`.

The earlier `short-screen-hot-cache.json` result is invalid for generated
performance comparison. DynamoDB `AttributeValue` was represented as a sealed
interface behind a structure schema, planning rejected it, and the benchmark
measured cached generic fallback. That screen remains useful only as a record
of the publication-cache investigation.

The valid screen passes the codec and end-to-end thresholds only for GetItem M.
It fails the multiple-workload requirement and exceeds the 2% regression limit
on GetItem L protocol deserialization and both PutItem surfaces. No
production-to-monolithic A/B/A comparison has been run, so the framework
extraction contract is also not established.

## Resource Screen

A cold GetItem L generation produced one 14,780-byte hidden class in 36.7 ms.
This is below the study's 193-590 ms runtime-`javac` range but is one root, not
a distribution. The valid JMH screen above records steady-state allocation.

Production metaspace, post-JIT code-cache size, compiler inlining logs, and
unloading after eviction have not been measured. The study ranges
(89-545 KiB generation metaspace and 339 KiB-1.78 MiB post-JIT code cache)
must not be treated as measurements of this ASM implementation.

## Deferred Validation

The full final, A/B/A, historical, metaspace, code-cache, inlining, and
unloading matrices are intentionally deferred. Run focused screens first and
keep each invocation below ten minutes:

```shell
./gradlew :benchmarks:serde-benchmarks:writeJmhClasspath
CP=$(cat benchmarks/serde-benchmarks/build/runtime-codegen/jmh-classpath.txt)
taskset -c 2 java -Xms1g -Xmx1g -XX:+UseG1GC -XX:+AlwaysPreTouch \
  -XX:ActiveProcessorCount=2 -Dsmithy-java.json-provider=smithy \
  -cp "$CP" org.openjdk.jmh.Main '.*AwsJsonRuntimeCodegen.*' \
  -bm avgt -tu ns -wi 3 -i 5 -w 2s -r 5s -f 1 -t 1 -prof gc
```

Run the monolithic study command from `JSON_PERFORMANCE_STUDY.md` on commit
`c74efb0c2` in a separate worktree, never concurrently with this screen.

## Adoption Decision

Reject adoption of the current generated JSON backend. Keep it disabled by
default and continue only as an internal experiment while the allocation and
large/serialization regressions are investigated. Do not enable the AWS JSON
integration flag in production.

Reconsider only after all of these hold:

- at least 10% codec-only improvement on multiple medium/large workloads;
- at least 5% AWS JSON end-to-end improvement on multiple workloads;
- no repeatable JSON regression above 2% against the monolithic study
  prototype;
- no semantic regressions in protocol and codec tests;
- bounded class, metaspace, and code-cache growth;
- reliable concurrent publication, fallback, eviction, and unloading.
