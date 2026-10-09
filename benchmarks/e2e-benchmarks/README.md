# smithy-java serde E2E benchmarks

Measures operations per process CPU-second for complete generated-client calls: serialization,
endpoint resolution, SigV4 signing, retries and interceptors, and deserialization. The default
transport is an in-process stub; socket setup, TLS, network and service time are excluded.
Inputs and clients are constructed before measurement and reused. Streaming responses are drained.
Live AWS workloads are in [`../live-benchmarks`](../live-benchmarks/README.md).

The cross-SDK model and methodology are in
[AwsSdkPerformanceBenchmarkModels](https://code.amazon.com/packages/AwsSdkPerformanceBenchmarkModels/trees/mainline/--/results):
[`ocs.md`](https://code.amazon.com/packages/AwsSdkPerformanceBenchmarkModels/blobs/mainline/--/results/ocs.md)
defines the measurement, and
[`ocs-sop.md`](https://code.amazon.com/packages/AwsSdkPerformanceBenchmarkModels/blobs/mainline/--/results/ocs-sop.md)
describes metal runs and submission.

## Cases

This module generates five clients from [`../serde-benchmarks/model`](../serde-benchmarks/model)
and reads its `serde-benchmark`-tagged request and response tests at runtime.

| Protocol | Service | Cases |
|---|---|---:|
| `awsJson1_0` | AwsJsonRpc10DataPlane | 22 |
| `rpcv2Cbor` | SmithyRpcV2CborDataPlane | 19 |
| `awsQuery` | AwsQueryDataPlane | 10 |
| `restJson1` | AwsRestJsonDataPlane | 10 |
| `restXml` | AwsRestXmlDataPlane | 10 |
| Total | | 71 |

Ids retain the model's names, such as `awsJson1_0_PutItemRequest_ShallowMap_M`. The canonical set
is checked in as `src/main/resources/.../canonical-benchmarks.txt` and validated at startup.
`--all-model-cases` adds smithy-java's `WideTypes` and `OutOfOrder` cases.

Each request case uses its `params` and receives a minimal valid response: `{}` for JSON, an empty
map for CBOR, no body for REST-XML, or an `<OpResponse><OpResult/></OpResponse>` wrapper for Query.
Each response case uses its fixture and sends an input containing only required members and URI
labels. Cases are measured independently.

CBOR bodies and `@httpPayload` blob fixtures are base64 in this model. The harness decodes them
so `ContentLength`, `Content-Length` and CRC64NVME describe the bytes sent. Structured blobs,
including DynamoDB `B` attributes, retain protocol-test UTF-8 semantics. The stub checks request
body length against `Content-Length` before measurement.

## Build and run

```bash
./gradlew :benchmarks:e2e-benchmarks:shadowJar
java -jar benchmarks/e2e-benchmarks/build/libs/smithy-java-e2e-benchmark.jar \
    --instance-type m7i.metal-24xl --output run1.json
```

The jar targets Java 21 and records its build commit. Metal runs default to Corretto 25.
The runner starts one child JVM per protocol with `-Xbatch`, forwards the launcher's JVM flags,
and merges the results. Progress goes to stderr; the summary goes to stdout. Any operation
failure fails the run. Tests invoke every model case once.

| Option | Default | Purpose |
|---|---|---|
| `--protocol NAMES` | all | Comma-separated protocol names from the table |
| `--filter SUBSTRINGS` | none | Case-insensitive substrings of benchmark ids |
| `--all-model-cases` | off | Include smithy-java-only cases |
| `--list` | off | List selected ids and exit |
| `--transport MODE` | `stub` | `http` or `https` uses a fixture server |
| `--endpoint URL` | mode default | `http://127.0.0.1:8080` or `https://127.0.0.1:8443`; scheme must match mode |
| `--min-measure-cpu-seconds S` | `1` | CPU-time floor; `0` restores the literal cross-SDK stop rule |
| `--in-process` | off | Run in this JVM for profiling; supply `-Xbatch` yourself |
| `--output PATH` | timestamped JSON | Results file |
| `--instance-type TYPE` | IMDSv2 lookup | Instance label in metadata |
| `--notes TEXT` | none | Run annotation |

```bash
java -jar smithy-java-e2e-benchmark.jar --protocol restXml --filter GetObject --output restxml.json
java -jar smithy-java-e2e-benchmark.jar --list
```

## Measurement

```text
warm up
System.gc()
cpuBefore = process CPU time
repeat:
    client.operation(input)
    iterations++
    every 100 iterations:
        stop if (iterations >= 50,000 OR CPU elapsed >= 5 s) AND CPU elapsed >= 1 s
ops/CPU-sec = iterations / CPU elapsed
```

Process CPU time includes user and system time for all threads, including JIT and GC. The
one-second floor limits quantization from Linux's 10 ms process CPU clock. Increase it for
longer profiling windows. A nanosecond-resolution benchmark-thread CPU metric is also recorded,
but excludes work on other threads.

Automatic warmup uses 2,000-call chunks. It stops after compilation stays below 2% of chunk
wall time for five consecutive chunks and at least one second, with a 20,000-call floor and
2,000,000-call cap. `-Xbatch` keeps compilation synchronous. A measured window spending more
than 5% of wall time compiling is flagged `under_warmed`.

The results schema is `smithy-java/e2e-ops-cpusec/1`. Metadata records the measurement settings,
transport, commit, instance, JVM, OS and CPU. Each case records iterations, CPU and wall time,
warmup, JIT and GC activity, and request/response verification. Summary values are geometric
means, with CPU/wall and warmup diagnostics.

## Real transport and fixture server

`http` and `https` measure smithy-java's HTTP client against the same response fixtures.
These runs include transport cost and cannot be compared with stub runs. HTTP/1.1 is enforced;
HTTPS uses BoringSSL. Certificate verification is disabled for the self-signed fixture
certificate, and this is recorded in metadata.

The fixture server has its own source set (`src/fixtureServer`) and jar. It uses one platform
thread per connection, blocking I/O, and the client's `SSLEngineTransport` for BoringSSL TLS.
Request framing reuses a buffer; response bytes are prepared at startup. Bodies, including
chunked uploads and trailers, are drained. HEAD, keep-alive and `Expect: 100-continue` are supported.

```bash
# Build both jars, generate a certificate, and run the pilot cases three times per mode.
./gradlew :benchmarks:e2e-benchmarks:transportBenchmark

./gradlew :benchmarks:e2e-benchmarks:transportBenchmark \
    -Ptransport=http -Pbenchmarks=rpcv2Cbor_PutItemRequest_Baseline -Pruns=5
```

`fixture/run-transport.py` starts servers on free loopback ports and runs independent client
JVMs with `-Xbatch` in randomized mode order. Results go to `build/transport-benchmark/`.
HTTPS certificates are generated with `fixture/make-cert.sh` using OpenSSL.
Server CPU per operation includes startup and warmup; it is omitted when failed runs or retries
make the request count incomplete.

For manual runs, export one case and use its `.fixture.json` `server_args` to configure the server:

```bash
java -jar smithy-java-e2e-benchmark.jar export-fixture restXml_GetObject_L --out fixtures
java -jar smithy-java-fixture-server.jar --help
java -jar smithy-java-e2e-benchmark.jar --protocol restXml --filter restXml_GetObject_L \
    --transport https --endpoint https://127.0.0.1:8443
```

The server serves one fixture per process. Each client run first checks the response status and
declared body length outside the measured window.

## Baseline comparison

Build the same harness and model against the baseline and current SDK. The cross-SDK baseline
is the last commit on or before 2026-02-01; copy the current benchmark modules and their
`settings.gradle.kts` includes onto that checkout, adapting APIs if needed and recording those
changes in `--notes`. Interleave three samples per side on the same host.

```bash
java -jar smithy-java-e2e-benchmark.jar compare \
    --baseline runs/baseline --current runs/current \
    --out results/smithy-java/m7imetal24xl_ocs_results
```

Each side accepts a file or directory. Directories concatenate protocol runs and use medians
for repeated cases; all samples within a side must use the same SDK, environment and measurement
configuration. Between sides, different measurement rules, client modes or transports are errors.
Different case sets require `--allow-partial`. CPU, instance, JVM and warmup differences produce
warnings. Output is `<out>.json` and `<out>.md`; `--lang` changes the SDK label.

Submit these files to `results/smithy-java/` in AwsSdkPerformanceBenchmarkModels and regenerate
the aggregate with `node scripts/markdown-ocs.js`.

## Metal runner

`scripts/run-metal-benchmarks.py` builds and stages artifacts through S3, launches a metal EC2
instance, and drives it through SSM without SSH or a key pair. `scripts/metal-host.sh` installs
Corretto, sets the performance governor where available, disables Intel turbo, smoke-tests the
host, runs the matrix, and uploads results and logs.

```bash
python3 scripts/run-metal-benchmarks.py setup-infra
python3 scripts/run-metal-benchmarks.py --dry-run
python3 scripts/run-metal-benchmarks.py --baseline-jar /path/to/baseline.jar
python3 scripts/run-metal-benchmarks.py --instance-type m7g.metal --suite e2e,serde
python3 scripts/run-metal-benchmarks.py --suite fixture --samples 5
python3 scripts/run-metal-benchmarks.py --suite h1scaling --h1-baseline-jmh-jar /path/to/baseline-jmh.jar
```

Local results go to `build/metal-runs/<run-id>/`; S3 retains results and a log bundle.
The runner terminates the instance on completion or interruption. A host shutdown terminates
it after `--max-hours`. `--keep-instance` retains it; `resume` continues polling a detached run,
and `cleanup` terminates an instance recorded in `metal-run-state.json`. Failed or unconfirmed
termination retains that file for cleanup. Networking and credentials can be overridden with
`--profile`, `--bucket`, `--instance-profile`, `--subnet-id` and `--no-public-ip`.

## JMH

The same cases are available in JMH sample mode for latency percentiles, with
`OpsPerCpuSecondProfiler` as a secondary metric:

```bash
./gradlew :benchmarks:e2e-benchmarks:jmh
./gradlew :benchmarks:e2e-benchmarks:jmh -Pjmh.testCaseId=awsJson1_0_GetItemOutput_M -Pjmh.fast
```

Results go to `build/results/jmh/results.json`. Cross-SDK submissions use the CPU-time runner,
whose stop rule differs from JMH's timed iterations.
