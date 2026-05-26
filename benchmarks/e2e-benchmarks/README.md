# smithy-java end-to-end benchmark runner

A workload-driven runner that exercises the smithy-java SDK against live AWS
services. It implements the same workload spec as the Java SDK v2 reference
runner at
[aws/e2e-benchmark-framework](https://github.com/aws/e2e-benchmark-framework/tree/main/runners/java-workload-runner)
so results are directly comparable across SDKs.

## Scope

| Service  | Operation | Variant   |
|----------|-----------|-----------|
| DynamoDB | GetItem   | Latency   |
| DynamoDB | PutItem   | Latency   |
| S3       | PutObject | Throughput|
| S3       | GetObject | Throughput|

Only the **synchronous** client mode is supported. smithy-java does not
generate async clients today, so a `--client async` argument is accepted but
prints a warning and proceeds with the sync client. Throughput tests still
push the SDK through a thread pool, which is the SDK's idiomatic concurrency
mechanism.

## Build

```bash
./gradlew :benchmarks:e2e-benchmarks:shadowJar
```

The jar lands at
`benchmarks/e2e-benchmarks/build/libs/smithy-java-e2e-benchmark-runner.jar`.

The build pulls AWS service models from Maven (`software.amazon.api.models:dynamodb`
and `software.amazon.api.models:s3`) and codegens the typed DynamoDB and S3
clients into separate packages so the same shape names from the two services
don't collide.

## Run

```bash
java -jar build/libs/smithy-java-e2e-benchmark-runner.jar \
     workloads/ddb-getitem-1KiB-latency-benchmark.json us-east-1
```

Region passed on the command line is ignored — the workload JSON is the
source of truth, matching the format expected by the framework's
`run-benchmark.py`.

The four workload files included here are byte-identical copies of the
specification's defaults; you can also point the runner at any v1 workload
JSON.

## Drive from the framework

```bash
python3 ../../path/to/e2e-benchmark-framework/scripts/run-benchmark.py \
  --runner "java -jar $(pwd)/build/libs/smithy-java-e2e-benchmark-runner.jar" \
  --region us-east-1 \
  --table benchmark-table \
  --bucket my-benchmark-bucket--use1-az4--x-s3
```

## Credentials

Credentials come from the AWS SDK v2 default credential provider chain
(env vars, profile file, container roles, IMDS). Override via the standard
SDK v2 environment variables.
