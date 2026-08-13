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

- Published baseline classes and generated codec classes target Java 21.
  Runtime generation itself is available only on JDK 24 or newer; Java 21-23
  always use the generic codec.
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
- JDK ClassFile API emission and hidden-class definition;
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

The implementation uses the finalized JDK ClassFile API on JDK 24 or newer.
The published artifact remains Java 21 compatible:

- schema planning, instruction recording, cache management, feature selection,
  diagnostics, and generic fallback compile to Java 21;
- the ClassFile lowering implementation is compiled in a JDK 24 source set and
  packaged in the same internal artifact;
- Java 21 code does not statically link `java.lang.classfile`; it loads the
  lowering implementation through a cached method handle only after the
  runtime-version gate succeeds;
- the recorded generation model lowers completely to codec-specific bytecode
  and is not retained by generated classes or used on runtime codec paths;
- generated hidden classes still target Java 17 classfile version 61, matching
  the previous emitter and remaining below the production Java 21 baseline.

This follows the historical production pattern: a higher-JDK implementation
is opportunistically available without raising the library baseline. Java
21-23 ignore enabled generation properties and continue through the generic
provider. JDK 24 linkage or emission failures are cached and use the same
fallback path as unsupported schemas.

ASM was removed from the dependency graph and no bytecode library is shaded.
The ClassFile API owns constant-pool construction, branch layout, stack maps,
and verifier-correct class emission. Runtime `javac` remains rejected because
the study measured 193-590 ms per root, requires a full compiler, and adds
source-generation and isolation costs.

## Planning And Splitting

Plans classify every reachable member by Smithy type, wire name, Java getter,
builder setter, nullability, collection element/value type, timestamp format,
and recursive edge. Unsupported edges reject the complete root plan.

Plans carry exact member ranges rather than only a helper count. The JSON
writer targets an estimated 220 inlined bytecodes per helper and assigns
different costs to presence checks, scalar fields, and aggregate calls. String
members use a fused field-token/value primitive, while cold buffer growth is
kept outside the normally inlined capacity check. Reader dispatch uses bounded
hash helpers at an estimated 300 bytecodes. These thresholds follow measured
C2 behavior: a 280-byte writer target produced two distinct compilation modes,
while 220 bytes kept both detached and protocol serialization stable.
Backends may lower the thresholds but may not defer splitting decisions to the
generated runtime path.

## Feature Gates

- `smithy-java.runtime-codegen=json` enables generated JSON codecs.
- `smithy-java.runtime-codegen=cbor` enables the CBOR proof slice.
- A comma-separated value enables both.
- `smithy-java.aws-json-runtime-codegen=true` enables the AWS JSON protocol
  experiment and implies JSON generation only for AWS JSON codec instances.

All gates are read when a codec is constructed. Changing a property does not
mutate an existing codec. On Java 21-23 all gates resolve to disabled.

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
validation/token-sink entry. Field names and encoded field tokens come from
schema extensions and become generated class constants. Canonical member order
uses exact precomputed token probes; reordered input falls back to bounded hash
dispatch. Generated map keys use a combined scan/decode primitive, and short
lists delay materialization so their final capacity is exact.

Writer helpers use the exact schema-plan ranges described above. String fields
reserve once for the member token and value, and the common writer capacity
check stays below C2's normal inline threshold.

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

### ClassFile emitter comparison

The ClassFile migration was measured separately from the generic-versus-
generated screen. A short generated-only A/B/A used ClassFile, the previous
ASM commit, and ClassFile again with identical JDK 26.0.1, G1, 1 GiB, CPU 2,
warmup, measurement, and GC-profiler settings:

| Workload | Surface | ClassFile A1 ns/op | ASM B ns/op | ClassFile A2 ns/op |
| --- | --- | ---: | ---: | ---: |
| GetItem M | codec deserialize | 4,406 | 4,687 | 4,441 |
| GetItem L | codec deserialize | 36,176 | 39,742 | 38,148 |
| GetItem M | protocol deserialize | 4,631 | 4,521 | 4,469 |
| GetItem L | protocol deserialize | 35,783 | 36,482 | 39,922 |
| PutItem mixed M | codec serialize | 4,287 | 3,920 | 4,038 |
| PutItem mixed M | protocol serialize | 3,963 | 4,186 | 3,932 |

The mixed direction and 11.6% spread between the two ClassFile GetItem L
protocol forks make this screen insufficient for a 2% regression decision.
Codec serialization repeatedly allocated 64 B/op more with ClassFile, while
protocol serialization allocated 64 B/op less. Deserialization allocation
also varied between forks. These effects require a longer multi-fork A/B/A
with compiler logs before being attributed to either emitter.

For the same planned GetItem L schema and generated name, both emitters
produced a 14,775-byte, 26-method class. Disassembly had identical method
instructions and bytecode offsets; only constant-pool numbering and ordering
differed. Generated operations do not call the instruction recorder or
ClassFile backend. The migration therefore preserves the runtime instruction
shape, but the short screen shows that JIT decisions remain sensitive enough
that steady-state equivalence cannot be claimed from one fork.

Raw results are in
`build/perf-study/runtime-codegen-production/classfile-short-screen.json`,
`build/perf-study/runtime-codegen-production/classfile-short-screen-a2.json`,
and `build/perf-study/runtime-codegen-production/asm-short-screen.json`.

## Fory Comparison

A matched comparison uses Apache Fory JSON source commit
`c50369695a2d123adf6d267d8f4032dfc602af10`. Fory code generation is enabled
with asynchronous compilation disabled, field access enabled, and one pooled
execution state. Smithy generation is required during setup; cached fallback
aborts the benchmark.

Both implementations serialize equivalent CloudWatch `PutMetricData` S/M/L
graphs. Fory uses benchmark-local mutable DTOs because it cannot bind directly
to Smithy's immutable builder-backed classes. Setup deserializes Fory output
into the Smithy model and requires equality with the original. Both
deserialization benchmarks consume the same canonical Smithy-produced UTF-8
bytes, and the Fory result must serialize back to the same Smithy model.

The object-construction contracts are not identical. Smithy invokes builder
setters, required-member validation, model constructors, and immutable
collection wrapping. Fory fills mutable fields. Serialization is the closer
comparison; deserialization deliberately includes each library's normal typed
model materialization.

The short screen ran on JDK 26.0.1, G1, fixed 1 GiB heap,
`AlwaysPreTouch`, one JMH thread, and CPU 2. It used two 1-second warmups and
three 2-second measurements with the GC profiler:

| Direction | Size | Smithy ns/op | Fory ns/op | Smithy/Fory | Smithy B/op | Fory B/op |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| serialize | S | 207 | 240 | 0.86x | 296 | 248 |
| serialize | M | 2,087 | 1,395 | 1.50x | 1,736 | 1,704 |
| serialize | L | 20,795 | 28,112 | 0.74x | 9,952 | 11,888 |
| deserialize | S | 556 | 272 | 2.04x | 888 | 664 |
| deserialize | M | 3,620 | 1,972 | 1.84x | 5,704 | 4,552 |
| deserialize | L | 34,112 | 19,468 | 1.75x | 44,776 | 37,672 |

An independent M/L confirmation reproduced the direction:

- M serialization: Smithy 1,986 ns/op, Fory 1,353 ns/op;
- L serialization: Smithy settled at 19,310-20,213 ns/op after the first
  measurement, Fory 28,335 ns/op;
- M deserialization: Smithy 3,587 ns/op, Fory 1,984 ns/op;
- L deserialization: Smithy 31,458 ns/op, Fory 19,580 ns/op.

Fory output was 2.7%, 1.2%, and 9.2% larger for S, M, and L because its DTO
double fields retain a decimal suffix where Smithy emits the shortest valid
number. The payloads are semantically equal, but byte counts must be considered
when interpreting the large serialization result.

Smithy serialization is therefore already competitive with Fory on this graph:
it wins S and L, including allocation on L, but remains about 47-50% slower on
M. Deserialization remains the clear gap at roughly 1.6-2.0x latency and
8-34% more allocation across the two screens.

Stack sampling attributes the Smithy reader gap primarily to general string
parsing and UTF-8 cache lookup, epoch timestamp conversion, the generic
floating-point parser, and immutable model/list construction. Fory's generated
path calls byte-native nullable-string and numeric token readers directly.
The next optimization priority is generated byte-cursor scalar/string/
timestamp parsing plus builder and collection materialization, not broader
writer fusion.

Results are in:

- `build/perf-study/fory-comparison/cloudwatch-smithy-vs-fory.json`;
- `build/perf-study/fory-comparison/cloudwatch-smithy-vs-fory-confirm.json`;
- `build/perf-study/fory-comparison/deserialize-m-stack.txt`.

## Resource Screen

A matched cold GetItem L probe produced the same 14,780-byte hidden class with
both emitters. ClassFile generation took 28.1 ms and ASM took 32.5 ms in one
cold sample. This is below the study's 193-590 ms runtime-`javac` range but is
one root, not a distribution. The shaded `codec-commons` artifact decreased
from 423,614 to 315,371 bytes after ASM removal, a 105.7 KiB reduction. The
valid JMH screens above record steady-state allocation.

Production metaspace, post-JIT code-cache size, compiler inlining logs, and
unloading after eviction have not been measured. The study ranges
(89-545 KiB generation metaspace and 339 KiB-1.78 MiB post-JIT code cache)
must not be treated as measurements of this implementation.

## Deferred Validation

The full final, A/B/A, historical, metaspace, code-cache, inlining, and
unloading matrices are intentionally deferred. Run focused screens first and
keep each invocation below ten minutes:

```shell
./gradlew \
  :codecs:codec-commons:jdk21Test \
  :codecs:json-codec:jdk24CodegenTest \
  :codecs:cbor-codec:jdk24CodegenTest

./gradlew :benchmarks:serde-benchmarks:writeJmhClasspath
CP=$(cat benchmarks/serde-benchmarks/build/runtime-codegen/jmh-classpath.txt)
taskset -c 2 java -Xms1g -Xmx1g -XX:+UseG1GC -XX:+AlwaysPreTouch \
  -XX:ActiveProcessorCount=2 -Dsmithy-java.json-provider=smithy \
  -cp "$CP" org.openjdk.jmh.Main '.*AwsJsonRuntimeCodegen.*' \
  -bm avgt -tu ns -wi 3 -i 5 -w 2s -r 5s -f 1 -t 1 -prof gc
```

Run the monolithic study command from `JSON_PERFORMANCE_STUDY.md` on commit
`c74efb0c2` in a separate worktree, never concurrently with this screen.
For the emitter comparison, run the generated-only command on the current
commit and `9dbd421d9` in ClassFile/ASM/ClassFile order with at least five
forks per position.

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
