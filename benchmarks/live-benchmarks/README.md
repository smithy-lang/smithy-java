# smithy-java live AWS benchmark runner

A small fixed-workload runner that exercises the smithy-java SDK against live AWS services.

This is not the cross-SDK "serde E2E" suite. That lives in
[`../e2e-benchmarks`](../e2e-benchmarks/README.md) and mocks the network
in-process so its ops/CPU-sec numbers are comparable across SDKs. The
benchmarks here include real network, TLS and service time, so their numbers
are only comparable with other live runs of the same workload.

Each measurement reports `ops_per_cpu_sec` using process CPU time alongside
latency and throughput. Set `-Dlive.collectMetrics=false` to exclude
resource-monitoring overhead.

## Scope

| Service  | Operation | Variant   |
|----------|-----------|-----------|
| DynamoDB | GetItem   | Latency   |
| DynamoDB | PutItem   | Latency   |
| S3       | PutObject | Throughput|
| S3       | GetObject | Throughput|

## Build

```bash
./gradlew :benchmarks:live-benchmarks:shadowJar
```

The jar lands at `benchmarks/live-benchmarks/build/libs/smithy-java-live-benchmark-runner.jar`.

The build pulls AWS service models from Maven (`software.amazon.api.models:dynamodb`
and `software.amazon.api.models:s3`) and codegens the typed DynamoDB and S3
clients into separate packages so the same shape names from the two services
don't collide.

## Run

```bash
java -jar build/libs/smithy-java-live-benchmark-runner.jar \
     --operation s3-put \
     --bucket my-bench--use1-az4--x-s3 \
     --region us-east-1
```

Operations:

| Operation | Workload |
|-----------|----------|
| `s3-put`  | S3 PutObject, 256 KiB body, concurrent |
| `s3-get`  | S3 GetObject, 256 KiB body, concurrent |
| `ddb-put` | DynamoDB PutItem, 1 KiB item, sequential |
| `ddb-get` | DynamoDB GetItem, 1 KiB item, sequential |

Flags:

| Flag | Notes |
|------|-------|
| `--operation <name>` | One of `s3-put`, `s3-get`, `ddb-put`, `ddb-get` |
| `--bucket <name>` | S3 Express directory bucket: `<base>--<az>--x-s3` |
| `--table <name>`  | Must have a `String` partition key named `pk` |
| `--region <region>` | Use the region the bucket / table lives in |
| `--client sync\|async` | Accepted for compatibility; smithy-java only supports sync |
| `--key-prefix <prefix>` | DynamoDB key prefix, default `item-` |
| `--s3-key-prefix <prefix>` | S3 key prefix, default `objects/256KiB/` |

Common system properties:

| Property | Default |
|----------|---------|
| `live.batch.actions` | `10000` for S3, `1000` for DynamoDB |
| `live.warmup.batches` | `2` |
| `live.measurement.batches` | `3` |
| `live.collectMetrics` | `true` |
| `live.object.size` | `262144` |
| `live.data.length` | `1024` |
| `live.ddb.createTable` | `true` |
| `live.ddb.deleteTable` | `true` |
| `live.ddb.readCapacityUnits` | `5000` |
| `live.ddb.writeCapacityUnits` | `5000` |

## Provision the resources

Run from an EC2 instance in the same AZ as the bucket. The instance role
needs `s3:CreateSession`, `s3express:*`, and `dynamodb:GetItem`/`PutItem`
on the resources below.

S3 Express directory bucket (replace `my-bench` and `use1-az4` with your
own base name and availability zone):

```bash
aws s3api create-bucket \
  --bucket my-bench--use1-az4--x-s3 \
  --region us-east-1 \
  --create-bucket-configuration '{
    "Location": {"Type": "AvailabilityZone", "Name": "use1-az4"},
    "Bucket":   {"Type": "Directory", "DataRedundancy": "SingleAvailabilityZone"}
  }'
```

The S3 download workload reads keys named `objects/256KiB/<index>`. Pre-seed
them by running the upload workload once first.

The DynamoDB workloads create a fresh provisioned table by default using
`--table` as the base name. The actual table name gets a run-specific suffix,
uses a `String` partition key named `pk`, defaults to `5000` RCU / `5000` WCU,
and is deleted after the run. The GetItem workload seeds its keys before warmup.

To reuse an existing table instead:

```bash
java -Dlive.ddb.createTable=false -jar build/libs/smithy-java-live-benchmark-runner.jar \
     --operation ddb-get --table my-bench-table --region us-east-1
```

## Cleanup

```bash
aws s3 rm s3://my-bench--use1-az4--x-s3 --recursive --region us-east-1
aws s3api delete-bucket --bucket my-bench--use1-az4--x-s3 --region us-east-1
aws dynamodb delete-table --table-name my-bench-table --region us-east-1
```

## Concurrency

Throughput benchmarks dispatch each batch action onto a virtual thread,
gated by a semaphore. The cap is `cores × multiplier`, where `multiplier`
defaults to `4` and can be overridden:

```bash
java -Dlive.concurrency.multiplier=16 -jar build/libs/smithy-java-live-benchmark-runner.jar \
     --operation s3-put --bucket my-bench--use1-az4--x-s3 --region us-east-1
```

## Credentials

Credentials are resolved from EC2 IMDSv2 directly. The benchmark is meant
to run on EC2 against directory buckets in the same AZ. For local testing
or non-EC2 hosts, supply credentials by extending the `Clients` factory.
