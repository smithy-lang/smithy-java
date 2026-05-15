# serde-benchmarks

JMH microbenchmarks for the smithy-java codec stack: AWS JSON 1.0, restJson1,
restXml, RPC v2 CBOR, and AWS Query.

## What is measured

Each benchmark drives the corresponding smithy-java {@code ClientProtocol}'s
{@code createRequest} (serialize) or {@code deserializeResponse}
(deserialize) — the same protocol-level entry points a generated client
uses internally, minus the actual HTTP transport.

| Protocol | Serialize entry point | Deserialize entry point |
|---|---|---|
| `awsJson1_0` | `AwsJson1Protocol#createRequest` | `AwsJson1Protocol#deserializeResponse` |
| `awsQuery` | `AwsQueryClientProtocol#createRequest` | `AwsQueryClientProtocol#deserializeResponse` |
| `restJson1` | `RestJsonClientProtocol#createRequest` | `RestJsonClientProtocol#deserializeResponse` |
| `restXml` | `RestXmlClientProtocol#createRequest` | `RestXmlClientProtocol#deserializeResponse` |
| `rpcv2Cbor` | `RpcV2CborProtocol#createRequest` | `RpcV2CborProtocol#deserializeResponse` |

Each benchmark loop therefore measures the *same* per-iteration work the
production code does for that protocol: header / URI label / query string
binding (where applicable) plus body serialization on the way out, and the
symmetric work plus body deserialization on the way in.

The input/output shapes (and the `ApiOperation` singletons) are
**codegen-generated** at build time. Each protocol has its own
`smithy-build.json` projection in `client` mode, emitting into a distinct
Java sub-package (`...generated.awsjson10.model`, `...generated.awsquery.model`,
`...generated.restjson.model`, `...generated.restxml.model`,
`...generated.rpcv2cbor.model`) so same-named typed shapes from different
protocols don't collide.

Test cases come from each operation's `@httpRequestTests` /
`@httpResponseTests` traits, filtered to the ones tagged `serde-benchmark`.
At trial setup the benchmark renders the trait's `params` Node to JSON and
deserializes it through the codegen-generated input builder — that work
happens once and is not measured.

JMH is run in `SampleTime` mode so per-invocation latency samples are recorded;
this gives the percentile distribution (p50/p90/p95/p99) needed by the shared
serde benchmark output schema.

## Running

Run all benchmarks:

```
./gradlew :benchmarks:serde-benchmarks:jmh
```

Run a subset by class or test case ID:

```
./gradlew :benchmarks:serde-benchmarks:jmh -Pjmh.includes=AwsJson1_0
./gradlew :benchmarks:serde-benchmarks:jmh -Pjmh.includes=PutItem
```

The JMH JSON output is written to:

```
benchmarks/serde-benchmarks/build/results/jmh/results.json
```

## Producing the cross-language schema

After running the benchmarks, convert the JMH JSON into the shared output
schema (one `.json` and one `.md` file):

```
./gradlew :benchmarks:serde-benchmarks:convertJmhResults \
    -Pinstance=m7i.xlarge \
    -Pos="x86_64-linux Linux 6.1.161" \
    -PoutputPrefix=build/results/jmh/m7i_xlarge
```

Optional properties:

- `-Pinstance=<label>` — instance descriptor (default: `unknown`)
- `-Pos=<label>` — OS descriptor (default: from `os.name` + `os.version`)
- `-PoutputPrefix=<path>` — output filename prefix (default:
  `build/results/jmh/output`)

The resulting JSON conforms to the shared schema:

```json
{
  "metadata": {
    "lang": "Java",
    "software": [["smithy-java", "<version>"]],
    "os": "...",
    "instance": "...",
    "precision": "-9"
  },
  "serde_benchmarks": [
    {
      "id": "<test case id>",
      "n": 1234567,
      "mean": 948.0,
      "p50": 891.0,
      "p90": 960.0,
      "p95": 994.0,
      "p99": 1116.0,
      "std_dev": 15.0
    }
  ]
}
```

## Adding a new benchmark

1. Add a new `httpRequestTests` or `httpResponseTests` test case in the
   relevant `model/operations/*.smithy` file. Tag it `["serde-benchmark"]`.
2. Add the case's `id` to the `@Param({...})` list of the corresponding
   benchmark class (one per protocol+direction).
3. Re-run the benchmarks.

## Updating the benchmark model

The `model/` directory in this module is a copy of the shared benchmark model.
When the canonical model changes:

1. Re-copy the `.smithy` files into `model/`.
2. Re-run `./gradlew :benchmarks:serde-benchmarks:smithyBuild` to regenerate
   the typed shape classes and `ApiOperation` instances under
   `build/smithyprojections/serde-benchmarks/<protocol>-client/java-codegen/java`.
3. Re-run the benchmarks.

The runtime `BenchmarkContext` discovers the model files via a generated
`META-INF/smithy/manifest` (see `generateSmithyManifest` in `build.gradle.kts`).
