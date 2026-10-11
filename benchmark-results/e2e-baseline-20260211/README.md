# e2e-baseline-20260211

The benchmark used smithy-java 0.0.3 from branch `adwsingh/e2e-baseline-20260211` at commit `46e24226c`.
The measurements are from 2026-10-10.

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

Each case runs for at least 1 second of process CPU time.
After this minimum, measurement stops at 50,000 iterations or 5 seconds of process CPU time, whichever comes first.

## Results

The results use operations per CPU second (`ops/CPU-sec`).
For each case, the table uses the median of the 3 samples.
Each protocol row shows the geometric mean of these medians for that protocol.
The overall row shows the geometric mean of these medians across all 71 cases.

| Protocol (cases) | m7i stub | m7g stub | m7i https | m7g https |
|---|---:|---:|---:|---:|
| AwsJson1.0 (22) | 38,588 | 43,452 | 9,610 | 9,520 |
| AwsQuery (10) | 16,165 | 16,798 | 6,549 | 5,341 |
| RestJson1 (10) | 36,808 | 36,542 | 7,159 | 7,046 |
| RestXml (10) | 35,583 | 36,696 | 7,096 | 6,679 |
| RpcV2Cbor (19) | 42,199 | 42,614 | 9,937 | 9,496 |
| **Overall (71)** | **34,338** | **36,031** | **8,445** | **7,996** |
