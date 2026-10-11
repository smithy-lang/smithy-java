# e2e-baseline-20260211

The benchmark used smithy-java 0.0.3 from branch `adwsingh/e2e-baseline-20260211` at commit `df20d6edd`.
The measurements are from 2026-10-12.

## Environment

| Setting | m7i.metal-24xl | m7g.metal |
|---|---|---|
| CPU | Intel Xeon Platinum 8488C, 96 vCPU | AWS Graviton3, 64 vCPU |
| Memory | 384 GiB | 256 GiB |
| OS | Amazon Linux 2023.12, kernel 6.18.51 | Amazon Linux 2023.12, kernel 6.18.51 |
| JVM | Amazon Corretto 25.0.4.1+8-LTS, `-Xbatch` | Amazon Corretto 25.0.4.1+8-LTS, `-Xbatch` |
| CPU frequency | The governor uses `performance`. Turbo is off. | The frequency stays constant. |

## Runtime

| Setting or mode | Description |
|---|---|
| Cases | The benchmark uses 71 canonical cases shared across SDKs. |
| Samples | Each host and mode has 3 samples. Each sample comes from a separate JVM run. |
| stub | The client receives prepared responses within the process. The mode uses no sockets. |
| https | The client uses `java.net.http.HttpClient` to connect to a loopback test server. The connection uses HTTP/1.1 over TLS 1.3. |

Each case warms up for at least 100,000 calls and 20 seconds, whichever comes later.
Warmup then continues until JIT compilation stays below 2% of wall time for 5 consecutive chunks of 2,000 calls and at least 1 second.
Warmup gives up at twice the floor.
After warmup, each case runs for at least 1 second of process CPU time.
After this minimum, measurement stops at 50,000 iterations or 5 seconds of process CPU time, whichever comes first.

Response fixtures use the same wire bytes as the serde benchmarks.
The harness decodes CBOR bodies and `@httpPayload` blobs from base64 and removes JSON whitespace outside strings.
XML bodies remain unchanged.

## Results

The results use operations per CPU second (`ops/CPU-sec`).
For each case, the table uses the median of the 3 samples.
Each protocol row shows the geometric mean of these medians for that protocol.
The overall row shows the geometric mean of these medians across all 71 cases.

| Protocol (cases) | m7i stub | m7g stub | m7i https | m7g https |
|---|---:|---:|---:|---:|
| AwsJson1.0 (22) | 38,616 | 44,296 | 9,738 | 9,526 |
| AwsQuery (10) | 16,175 | 16,782 | 6,511 | 5,221 |
| RestJson1 (10) | 36,598 | 37,687 | 7,085 | 6,988 |
| RestXml (10) | 36,253 | 36,636 | 6,974 | 6,488 |
| RpcV2Cbor (19) | 41,370 | 44,277 | 9,806 | 9,189 |
| **Overall (71)** | **34,229** | **36,766** | **8,409** | **7,861** |

On m7g.metal in `stub` mode, `restJson1_GetObject_S` is flagged under-warmed in all 3 samples.
JIT compilation stays above 2% of wall time for that case after warmup.
