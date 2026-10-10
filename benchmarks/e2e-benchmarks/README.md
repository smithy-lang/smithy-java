# smithy-java serde E2E benchmarks

Measures operations per process CPU-second for complete generated-client calls: serialization,
endpoint resolution, SigV4 signing, retries and interceptors, and deserialization. Inputs and clients
are built before measurement and reused. Streaming responses are drained. Live AWS workloads are in
[`../live-benchmarks`](../live-benchmarks/README.md).

The measurement follows the procedure shared by the AWS SDK teams: one loop per benchmark, process
CPU time, geometric means per protocol and overall, at least three interleaved baseline and current
samples on bare metal, medians per benchmark.

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

Ids are the model's test-case ids, such as `awsJson1_0_PutItemRequest_ShallowMap_M`. The canonical
set is checked in as `src/main/resources/.../canonical-benchmarks.txt` and validated at startup.
`--all-model-cases` adds `WideTypes` and `OutOfOrder` cases outside the canonical set.

Each request case uses its `params` and receives a minimal valid response: `{}` for JSON, an empty
map for CBOR, no body for REST-XML, or an `<OpResponse><OpResult/></OpResponse>` wrapper for Query.
Each response case uses its fixture and sends an input containing only required members and URI
labels. CBOR bodies and `@httpPayload` blob fixtures are base64 in this model; the harness decodes
them so `Content-Length` describes the bytes sent. The first call of every case checks that the
request body length matches `Content-Length` and that the response deserialized into the expected
output members.

## Run

```bash
./gradlew :benchmarks:e2e-benchmarks:shadowJar
java -Xbatch -jar benchmarks/e2e-benchmarks/build/libs/smithy-java-e2e-benchmark.jar \
    --instance-type m7i.metal-24xl --output run1.json

# Gradle adds -Xbatch.
./gradlew :benchmarks:e2e-benchmarks:run --args="--mode https --protocol rpcv2Cbor"
```

One JVM runs the selected cases. For each case, the harness sets the response, checks one call,
warms up, calls `System.gc()`, and measures. Pass `-Xbatch` to disable background JIT compilation.
Progress goes to stderr, the summary to stdout, and any failure fails the run.

| Option | Default | Purpose |
|---|---|---|
| `--mode stub\|https` | `stub` | See below |
| `--protocol NAMES` | all | Comma-separated protocol names from the table |
| `--filter SUBSTRINGS` | none | Case-insensitive substrings of benchmark ids |
| `--all-model-cases` | off | Include smithy-java-only cases |
| `--list` | off | List selected ids and exit |
| `--output PATH` | timestamped JSON | Results file |
| `--instance-type TYPE` | IMDSv2 lookup | Instance label in metadata |
| `--notes TEXT` | none | Run annotation |

### Modes

**`stub`** is the cross-SDK configuration: an in-process `ClientTransport` returns each case's
canned response without opening sockets.

**`https`** measures the same client calls through smithy-java's HTTP client (HTTP/1.1, BoringSSL,
TLS 1.3) to a fixture server. The harness starts the server in a child JVM from the same jar, so the
server's CPU time stays out of the measurement, and tells it which response to serve before each
case. Certificate verification is disabled: the server makes a self-signed certificate with
`keytool` at startup.

The fixture server (`...benchmarks.fixture.FixtureServer`) frames request headers in a reusable buffer,
drains the body, and writes the current response bytes. It has two ports. The HTTPS data port serves
the current fixture. The plaintext control port takes `POST /fixture` with the complete HTTP/1.1
response as the body, validates it, and swaps it in for every connection. Until the first fixture
arrives it answers 503.

## Measurement

```text
warm up: 2,000-call chunks until JIT compilation stays below 2% of chunk wall time
         for five consecutive chunks and at least one second (floor 20,000 calls, cap 2,000,000)
System.gc()
cpuBefore = process CPU time
repeat:
    client.operation(input)
    iterations++
    every 100 iterations:
        stop if (iterations >= 50,000 OR CPU elapsed >= 5 s) AND CPU elapsed >= 1 s
ops/CPU-sec = iterations / CPU elapsed
```

Process CPU time includes user and system time for all threads, including JIT and GC. Linux reads
it in 10 ms ticks, hence the one-second floor. A measured window that spends more than 5% of its
wall time compiling is flagged `under_warmed`.

The results schema is `smithy-java/e2e-ops-cpusec/2`. Metadata records the measurement settings,
mode, transport, commit, instance, JVM, OS and CPU. Each case records iterations, CPU and wall time,
warmup, JIT and GC activity, and a fingerprint of its inputs. Summary values are geometric means.

## Compare and submit

`scripts/compare-ocs.py` compares baseline and current runs using medians per benchmark.
Input files require a nonempty `benchmarks` list with `id` and `ops_per_cpu_sec`.
The report includes shared benchmark ids and records metadata and workload differences as warnings.
It writes `<out>.json` with `metadata`, `benchmarks`, and `summary`, and prints the summary.

```bash
python3 scripts/compare-ocs.py \
    --baseline runs/baseline/*.json --current runs/current/*.json \
    --out results/smithy-java/m7imetal24xl_ocs_results
```

## Metal runs

`scripts/run-metal-benchmarks.py` uploads the jar(s) to S3, launches a metal instance in us-east-1
with SSM only, and runs a short shell script on it: install Corretto, pin the CPU governor, download
the jars, run `java -Xbatch -jar` once per sample with baseline and current interleaved, upload the
results. It then downloads the results and terminates the instance.

```bash
python3 scripts/run-metal-benchmarks.py setup-infra            # once per account
python3 scripts/run-metal-benchmarks.py --dry-run
python3 scripts/run-metal-benchmarks.py --baseline-jar /path/to/baseline.jar --samples 3
python3 scripts/run-metal-benchmarks.py --instance-type m7g.metal --e2e-args "--mode https"
python3 scripts/run-metal-benchmarks.py cleanup                # after a crash
```

Results land in `build/metal-runs/<run-id>/results/<side>/sample<N>.json` and under
`s3://<bucket>/ops-cpusec/smithy-java/<arch>/<run-id>/`. `--keep-instance` retains the host; the
host also shuts itself down after `--max-hours`.

## JMH

The same cases are available in JMH sample mode for latency percentiles over the stub, with
`OpsPerCpuSecondProfiler` as a secondary metric:

```bash
./gradlew :benchmarks:e2e-benchmarks:jmh
./gradlew :benchmarks:e2e-benchmarks:jmh -Pjmh.testCaseId=awsJson1_0_GetItemOutput_M -Pjmh.fast
```

Results go to `build/results/jmh/results.json`. Cross-SDK submissions use the CPU-time runner.
