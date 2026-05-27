# smithy-java end-to-end benchmark runner

A workload-driven runner that exercises the smithy-java SDK against live AWS
services.

## Scope

| Service  | Operation | Variant   |
|----------|-----------|-----------|
| DynamoDB | GetItem   | Latency   |
| DynamoDB | PutItem   | Latency   |
| S3       | PutObject | Throughput|
| S3       | GetObject | Throughput|

## Build

```bash
./gradlew :benchmarks:e2e-benchmarks:shadowJar
```

The jar lands at `benchmarks/e2e-benchmarks/build/libs/smithy-java-e2e-benchmark-runner.jar`.

The build pulls AWS service models from Maven (`software.amazon.api.models:dynamodb`
and `software.amazon.api.models:s3`) and codegens the typed DynamoDB and S3
clients into separate packages so the same shape names from the two services
don't collide.

## Run

```bash
java -jar build/libs/smithy-java-e2e-benchmark-runner.jar \
     --bucket my-bench--use1-az4--x-s3 \
     --region us-east-1 \
     workloads/s3-upload-256KiB-throughput-benchmark.json
```

Flags (all optional, override the matching `actionConfig` fields in the workload JSON):

| Flag | Workload field | Notes |
|------|----------------|-------|
| `--bucket <name>` | `actionConfig.bucketName` | S3 Express directory bucket: `<base>--<az>--x-s3` |
| `--table <name>`  | `actionConfig.tableName`  | Must have a `String` partition key named `pk` |
| `--region <region>` | `actionConfig.region` | Use the region the bucket / table lives in |
| `--client sync\|async` | — | Accepted for compatibility; smithy-java only supports sync |

Workload JSON files in `workloads/` ship with placeholder names; supply your own
via flags rather than editing the files.

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

DynamoDB table:

```bash
aws dynamodb create-table \
  --region us-east-1 \
  --table-name my-bench-table \
  --attribute-definitions AttributeName=pk,AttributeType=S \
  --key-schema AttributeName=pk,KeyType=HASH \
  --billing-mode PAY_PER_REQUEST

aws dynamodb wait table-exists --table-name my-bench-table --region us-east-1
```

The DynamoDB GetItem workload reads items keyed `item-<index>`. Pre-seed
by running the PutItem workload first.

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
java -De2e.concurrency.multiplier=16 -jar build/libs/smithy-java-e2e-benchmark-runner.jar \
     --bucket my-bench--use1-az4--x-s3 --region us-east-1 \
     workloads/s3-upload-256KiB-throughput-benchmark.json
```

## Credentials

Credentials are resolved from EC2 IMDSv2 directly. The benchmark is meant
to run on EC2 against directory buckets in the same AZ. For local testing
or non-EC2 hosts, supply credentials by extending the `Clients` factory.
