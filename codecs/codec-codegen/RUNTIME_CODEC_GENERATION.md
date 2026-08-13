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
structures, generated unions, lists, maps, scalar primitives, blobs, string
enums, timestamps, documents, sparse aggregates, borrowed output, detached
output, full-model input, and an allocation-free validation/token-sink entry.
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
`AlwaysPreTouch`, one JMH thread, and CPU 2. It used one 1-second warmup and
two 3-second average-time measurements, so it is directional rather than an
adoption result.

| Workload | Surface | Generic ns/op | Generated ns/op | Change |
| --- | --- | ---: | ---: | ---: |
| GetItem M | codec deserialize | 5,101 | 5,318 | -4.3% |
| GetItem L | codec deserialize | 39,532 | 36,105 | +8.7% |
| GetItem M | protocol deserialize | 5,179 | 4,919 | +5.0% |
| GetItem L | protocol deserialize | 38,524 | 36,489 | +5.3% |
| PutItem mixed M | codec serialize | 4,004 | 4,023 | -0.5% |
| PutItem mixed M | protocol serialize | 4,495 | 3,984 | +11.4% |

After publication-cache correction, allocation was identical within rounding
for every pair. Results are in
`build/perf-study/runtime-codegen-production/short-screen-hot-cache.json`.
The earlier screen, which exposed the per-operation registry regression, is
retained beside it.

These results pass the 5% end-to-end threshold on the three measured cases but
do not pass the codec-only 10% threshold. GetItem M also exceeds the 2%
regression limit. No production-to-monolithic A/B/A comparison has been run,
so the framework extraction contract is not established.

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

## Adoption

Keep the implementation experimental unless all of these hold:

- at least 10% codec-only improvement on multiple medium/large workloads;
- at least 5% AWS JSON end-to-end improvement on multiple workloads;
- no repeatable JSON regression above 2% against the monolithic study
  prototype;
- no semantic regressions in protocol and codec tests;
- bounded class, metaspace, and code-cache growth;
- reliable concurrent publication, fallback, eviction, and unloading.
