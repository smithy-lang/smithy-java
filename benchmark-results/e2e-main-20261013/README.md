# e2e-main-20261013

The benchmark used smithy-java 1.7.0 from branch `main` at commit `304e02511`.
The measurements ran on 2026-10-13 local time (2026-10-12 22:28 to 23:50 UTC).
The [e2e-baseline-20260211](../e2e-baseline-20260211/README.md) run is the comparison baseline. Both runs use the same harness, hosts, modes and case set.

## Environment

| Setting | m7i.metal-24xl | m7g.metal |
|---|---|---|
| CPU | Intel Xeon Platinum 8488C, 96 vCPU | AWS Graviton3, 64 vCPU |
| Memory | 384 GiB | 256 GiB |
| OS | Amazon Linux 2023.12.20261012, kernel 6.18.54 | Amazon Linux 2023.12.20261012, kernel 6.18.54 |
| JVM | Amazon Corretto 25.0.4.1+10-LTS, `-Xbatch` | Amazon Corretto 25.0.4.1+10-LTS, `-Xbatch` |
| CPU frequency | The governor uses `performance`. Turbo is off. | The frequency stays constant. |

## Runtime

| Setting or mode | Description |
|---|---|
| Cases | The benchmark uses 71 canonical cases shared across SDKs. |
| Samples | Each host and mode has 3 samples. Each sample comes from a separate JVM run. |
| stub | The client receives prepared responses within the process. The mode uses no sockets. |
| https | The client uses smithy-java's HTTP client to connect to a loopback test server in a child JVM. The connection uses HTTP/1.1 over TLS 1.3. |

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
| AwsJson1.0 (22) | 155,449 | 175,710 | 36,759 | 42,192 |
| AwsQuery (10) | 82,780 | 96,537 | 27,127 | 30,022 |
| RestJson1 (10) | 156,662 | 187,639 | 27,328 | 31,562 |
| RestXml (10) | 153,565 | 172,954 | 27,150 | 31,082 |
| RpcV2Cbor (19) | 131,070 | 161,049 | 35,935 | 40,731 |
| **Overall (71)** | **135,815** | **158,886** | **32,171** | **36,633** |

## Difference from the baseline

Each cell shows the baseline geometric mean, the current geometric mean, and the ratio of current to baseline.
The baseline is smithy-java 0.0.3 at commit `df20d6edd`, measured on 2026-10-12 with the same harness.

| Protocol (cases) | m7i stub | m7g stub | m7i https | m7g https |
|---|---:|---:|---:|---:|
| AwsJson1.0 (22) | 38,616 → 155,449 (4.03x) | 44,296 → 175,710 (3.97x) | 9,738 → 36,759 (3.77x) | 9,526 → 42,192 (4.43x) |
| AwsQuery (10) | 16,175 → 82,780 (5.12x) | 16,782 → 96,537 (5.75x) | 6,511 → 27,127 (4.17x) | 5,221 → 30,022 (5.75x) |
| RestJson1 (10) | 36,598 → 156,662 (4.28x) | 37,687 → 187,639 (4.98x) | 7,085 → 27,328 (3.86x) | 6,988 → 31,562 (4.52x) |
| RestXml (10) | 36,253 → 153,565 (4.24x) | 36,636 → 172,954 (4.72x) | 6,974 → 27,150 (3.89x) | 6,488 → 31,082 (4.79x) |
| RpcV2Cbor (19) | 41,370 → 131,070 (3.17x) | 44,277 → 161,049 (3.64x) | 9,806 → 35,935 (3.66x) | 9,189 → 40,731 (4.43x) |
| **Overall (71)** | **34,229 → 135,815 (3.97x)** | **36,766 → 158,886 (4.32x)** | **8,409 → 32,171 (3.83x)** | **7,861 → 36,633 (4.66x)** |

### Per-case difference

Each cell shows the current median in `ops/CPU-sec` and the change from the baseline median.

| Case | m7i stub | m7g stub | m7i https | m7g https |
|---|---:|---:|---:|---:|
| `awsJson1_0_GetItemInput_Baseline` | 413,900 (+575.2%) | 464,400 (+569.2%) | 50,000 (+312.0%) | 62,000 (+405.9%) |
| `awsJson1_0_GetItemOutputBinary_L` | 31,646 (+75.3%) | 31,250 (+95.0%) | 19,841 (+181.8%) | 20,161 (+213.1%) |
| `awsJson1_0_GetItemOutputBinary_M` | 128,300 (+177.1%) | 164,700 (+239.3%) | 40,650 (+288.6%) | 46,296 (+362.0%) |
| `awsJson1_0_GetItemOutputBinary_S` | 290,600 (+487.0%) | 263,600 (+361.6%) | 46,296 (+318.5%) | 56,100 (+409.4%) |
| `awsJson1_0_GetItemOutput_Baseline` | 342,300 (+468.6%) | 509,600 (+543.4%) | 50,700 (+335.0%) | 63,900 (+434.2%) |
| `awsJson1_0_GetItemOutput_L` | 38,462 (+93.1%) | 45,045 (+158.6%) | 21,930 (+198.0%) | 23,923 (+241.8%) |
| `awsJson1_0_GetItemOutput_M` | 143,700 (+247.8%) | 165,500 (+267.4%) | 42,735 (+302.6%) | 50,000 (+393.0%) |
| `awsJson1_0_GetItemOutput_S` | 291,700 (+483.4%) | 337,400 (+417.5%) | 48,077 (+328.8%) | 58,600 (+414.5%) |
| `awsJson1_0_HealthcheckRequest_Example` | 406,300 (+563.9%) | 458,300 (+496.7%) | 50,800 (+344.0%) | 61,000 (+408.7%) |
| `awsJson1_0_HealthcheckResponse_Example` | 424,400 (+656.5%) | 405,500 (+435.0%) | 50,600 (+314.9%) | 61,300 (+414.9%) |
| `awsJson1_0_PutItemRequest_Baseline` | 388,800 (+498.2%) | 440,700 (+457.1%) | 49,505 (+333.7%) | 61,600 (+432.2%) |
| `awsJson1_0_PutItemRequest_BinaryData_L` | 11,601 (+189.2%) | 13,699 (+140.0%) | 6,740 (+184.9%) | 6,860 (+160.9%) |
| `awsJson1_0_PutItemRequest_BinaryData_M` | 80,900 (+260.8%) | 94,000 (+187.6%) | 28,409 (+228.0%) | 30,303 (+258.1%) |
| `awsJson1_0_PutItemRequest_BinaryData_S` | 345,600 (+426.0%) | 416,600 (+519.0%) | 50,000 (+318.0%) | 59,500 (+427.2%) |
| `awsJson1_0_PutItemRequest_MixedItem_L` | 56,900 (+131.0%) | 56,700 (+123.4%) | 27,027 (+200.3%) | 27,322 (+246.7%) |
| `awsJson1_0_PutItemRequest_MixedItem_M` | 125,300 (+168.1%) | 137,100 (+231.8%) | 38,168 (+258.8%) | 42,017 (+333.2%) |
| `awsJson1_0_PutItemRequest_MixedItem_S` | 290,700 (+460.1%) | 326,600 (+367.9%) | 48,077 (+321.2%) | 55,200 (+395.7%) |
| `awsJson1_0_PutItemRequest_Nested_L` | 206,400 (+333.4%) | 220,500 (+323.2%) | 45,455 (+313.6%) | 51,400 (+383.2%) |
| `awsJson1_0_PutItemRequest_Nested_M` | 331,200 (+421.6%) | 373,700 (+412.6%) | 49,020 (+319.6%) | 58,200 (+398.2%) |
| `awsJson1_0_PutItemRequest_ShallowMap_L` | 55,300 (+154.4%) | 59,300 (+131.3%) | 25,773 (+215.8%) | 27,933 (+250.0%) |
| `awsJson1_0_PutItemRequest_ShallowMap_M` | 131,300 (+188.9%) | 151,000 (+205.0%) | 38,168 (+261.1%) | 44,248 (+327.4%) |
| `awsJson1_0_PutItemRequest_ShallowMap_S` | 272,100 (+396.5%) | 365,500 (+461.4%) | 48,544 (+315.5%) | 58,600 (+421.5%) |
| `awsQuery_GetMetricDataRequest_L` | 55,300 (+214.1%) | 57,000 (+173.6%) | 25,000 (+230.7%) | 23,923 (+296.9%) |
| `awsQuery_GetMetricDataRequest_M` | 89,600 (+258.4%) | 116,300 (+379.2%) | 34,014 (+305.9%) | 38,168 (+465.7%) |
| `awsQuery_GetMetricDataRequest_S` | 222,400 (+620.6%) | 249,700 (+599.2%) | 45,045 (+345.0%) | 52,000 (+595.2%) |
| `awsQuery_GetMetricDataResponse_L` | 7,460 (+373.1%) | 7,960 (+468.6%) | 5,140 (+351.8%) | 5,600 (+437.8%) |
| `awsQuery_GetMetricDataResponse_M` | 51,800 (+310.3%) | 66,900 (+466.0%) | 26,316 (+315.1%) | 29,070 (+489.6%) |
| `awsQuery_GetMetricDataResponse_S` | 135,600 (+477.7%) | 143,000 (+514.9%) | 38,462 (+340.1%) | 44,248 (+587.1%) |
| `awsQuery_PutMetricDataRequest_Baseline` | 350,500 (+937.5%) | 470,100 (+1197.5%) | 50,000 (+389.0%) | 62,300 (+670.7%) |
| `awsQuery_PutMetricDataRequest_L` | 19,455 (+182.8%) | 21,097 (+176.9%) | 11,574 (+206.2%) | 11,628 (+244.7%) |
| `awsQuery_PutMetricDataRequest_M` | 123,700 (+389.9%) | 142,500 (+475.7%) | 38,168 (+341.8%) | 41,322 (+502.4%) |
| `awsQuery_PutMetricDataRequest_S` | 310,200 (+843.0%) | 394,600 (+957.5%) | 49,020 (+383.3%) | 58,100 (+626.2%) |
| `restJson1_CopyObjectOutput_Baseline` | 355,200 (+427.0%) | 413,200 (+640.5%) | 50,000 (+311.0%) | 60,000 (+402.8%) |
| `restJson1_CopyObjectOutput_M` | 192,600 (+289.1%) | 229,400 (+331.2%) | 40,984 (+309.8%) | 48,077 (+392.6%) |
| `restJson1_CopyObjectRequest_Baseline` | 367,900 (+575.0%) | 417,500 (+641.6%) | 50,000 (+297.0%) | 59,500 (+397.4%) |
| `restJson1_CopyObjectRequest_M` | 88,700 (+485.4%) | 124,600 (+704.9%) | 33,784 (+521.0%) | 38,168 (+654.3%) |
| `restJson1_GetObject_L` | 128,800 (+153.5%) | 129,200 (+179.1%) | 5,389 (+297.1%) | 5,768 (+210.8%) |
| `restJson1_GetObject_M` | 281,000 (+378.7%) | 335,000 (+423.4%) | 44,643 (+290.2%) | 52,400 (+387.3%) |
| `restJson1_GetObject_S` | 345,800 (+582.1%) | 354,600 (+609.2%) | 45,455 (+308.2%) | 52,700 (+403.8%) |
| `restJson1_PutObject_L` | 4,380 (+5.8%) | 6,048 (+9.8%) | 2,704 (+56.9%) | 2,988 (+73.7%) |
| `restJson1_PutObject_M` | 214,400 (+380.3%) | 311,900 (+530.0%) | 46,296 (+314.8%) | 54,800 (+423.9%) |
| `restJson1_PutObject_S` | 339,400 (+506.1%) | 379,000 (+585.4%) | 49,020 (+326.5%) | 57,400 (+447.6%) |
| `restXml_CopyObjectOutput_Baseline` | 325,600 (+487.7%) | 325,900 (+405.3%) | 50,000 (+311.0%) | 57,800 (+405.2%) |
| `restXml_CopyObjectOutput_M` | 218,100 (+602.3%) | 233,500 (+693.9%) | 40,000 (+332.0%) | 45,872 (+566.7%) |
| `restXml_CopyObjectRequest_Baseline` | 339,200 (+383.9%) | 331,500 (+408.4%) | 48,544 (+290.3%) | 56,500 (+389.3%) |
| `restXml_CopyObjectRequest_M` | 89,500 (+492.5%) | 119,300 (+708.9%) | 33,784 (+529.2%) | 37,879 (+677.8%) |
| `restXml_GetObject_L` | 128,000 (+189.3%) | 126,300 (+175.3%) | 5,200 (+290.4%) | 5,748 (+220.6%) |
| `restXml_GetObject_M` | 270,700 (+311.4%) | 323,100 (+405.6%) | 44,643 (+303.6%) | 52,200 (+401.1%) |
| `restXml_GetObject_S` | 340,000 (+523.9%) | 363,500 (+596.4%) | 46,729 (+300.0%) | 55,000 (+433.5%) |
| `restXml_PutObject_L` | 4,400 (+6.3%) | 6,040 (+9.4%) | 2,695 (+62.3%) | 2,968 (+76.7%) |
| `restXml_PutObject_M` | 243,800 (+317.5%) | 289,700 (+502.6%) | 46,296 (+308.3%) | 53,500 (+464.3%) |
| `restXml_PutObject_S` | 267,700 (+377.2%) | 306,600 (+421.4%) | 49,020 (+339.2%) | 56,600 (+481.1%) |
| `rpcv2Cbor_GetItemOutputBinary_L` | 32,680 (+19.0%) | 39,370 (+55.1%) | 20,833 (+157.8%) | 21,930 (+190.7%) |
| `rpcv2Cbor_GetItemOutputBinary_M` | 116,900 (+161.9%) | 144,900 (+207.2%) | 39,370 (+266.9%) | 43,478 (+346.4%) |
| `rpcv2Cbor_GetItemOutputBinary_S` | 262,300 (+286.9%) | 314,400 (+351.1%) | 45,872 (+311.0%) | 54,059 (+413.6%) |
| `rpcv2Cbor_GetItemOutput_Baseline` | 346,600 (+373.5%) | 415,200 (+448.5%) | 49,505 (+344.6%) | 59,200 (+425.7%) |
| `rpcv2Cbor_GetItemOutput_L` | 35,461 (+56.0%) | 36,232 (+63.0%) | 21,097 (+166.4%) | 22,321 (+204.9%) |
| `rpcv2Cbor_GetItemOutput_M` | 127,600 (+196.0%) | 179,100 (+219.3%) | 40,323 (+291.1%) | 45,045 (+357.8%) |
| `rpcv2Cbor_GetItemOutput_S` | 255,900 (+387.4%) | 261,500 (+353.2%) | 46,729 (+310.3%) | 54,000 (+418.4%) |
| `rpcv2Cbor_PutItemRequest_Baseline` | 340,000 (+500.7%) | 415,500 (+571.2%) | 48,544 (+323.3%) | 57,600 (+439.1%) |
| `rpcv2Cbor_PutItemRequest_BinaryData_L` | 15,924 (+55.4%) | 21,368 (+45.3%) | 8,902 (+120.8%) | 9,660 (+139.6%) |
| `rpcv2Cbor_PutItemRequest_BinaryData_M` | 95,800 (+172.1%) | 130,600 (+210.8%) | 33,113 (+223.8%) | 38,760 (+324.1%) |
| `rpcv2Cbor_PutItemRequest_BinaryData_S` | 269,000 (+383.8%) | 396,000 (+441.7%) | 48,077 (+316.3%) | 57,700 (+420.5%) |
| `rpcv2Cbor_PutItemRequest_MixedItem_L` | 67,200 (+128.5%) | 74,100 (+186.0%) | 30,120 (+242.3%) | 32,051 (+313.0%) |
| `rpcv2Cbor_PutItemRequest_MixedItem_M` | 136,100 (+188.5%) | 166,000 (+251.9%) | 40,000 (+280.8%) | 45,045 (+368.2%) |
| `rpcv2Cbor_PutItemRequest_MixedItem_S` | 265,100 (+334.6%) | 324,100 (+487.1%) | 47,170 (+324.5%) | 54,500 (+421.0%) |
| `rpcv2Cbor_PutItemRequest_Nested_L` | 189,000 (+300.7%) | 244,600 (+321.7%) | 45,455 (+312.7%) | 51,700 (+391.1%) |
| `rpcv2Cbor_PutItemRequest_Nested_M` | 292,700 (+440.0%) | 361,900 (+530.5%) | 48,544 (+332.0%) | 56,200 (+427.2%) |
| `rpcv2Cbor_PutItemRequest_ShallowMap_L` | 65,600 (+172.9%) | 70,300 (+178.4%) | 28,736 (+229.5%) | 31,056 (+300.2%) |
| `rpcv2Cbor_PutItemRequest_ShallowMap_M` | 141,700 (+206.1%) | 168,400 (+331.1%) | 40,650 (+288.6%) | 46,729 (+394.0%) |
| `rpcv2Cbor_PutItemRequest_ShallowMap_S` | 244,200 (+301.0%) | 352,900 (+424.4%) | 48,077 (+342.3%) | 56,700 (+419.4%) |

## Notes

Every case is faster than the baseline on every host and mode.
On m7i.metal-24xl in `stub` mode, the smallest gains are `restJson1_PutObject_L` (1.06x) and `restXml_PutObject_L` (1.06x).
The largest gains are `awsQuery_PutMetricDataRequest_Baseline` (10.37x) and `awsQuery_PutMetricDataRequest_S` (9.43x).
Large blob uploads spend most of their CPU time hashing and copying the payload, so they gain the least.

On m7g.metal in `stub` mode, `restJson1_GetObject_S` is flagged under-warmed in 3 of 3 samples.
JIT compilation stays above 2% of wall time for that case after warmup, as in the baseline run.

In `https` mode, the client's CPU-to-wall ratio is 0.64 on m7i.metal-24xl and 0.76 on m7g.metal, compared with about 1.0 in the baseline run.
Each call is synchronous: the client thread blocks in the socket read while the test server handles the request.
The client spends about 21 µs of CPU per small call on m7i.metal-24xl, and the blocked time per call is about 16 µs on m7i.metal-24xl and 7 µs on m7g.metal, so the wait is a large share of wall time.
The baseline client used `java.net.http.HttpClient`, whose selector and executor threads consumed CPU during the wait, and spent about 88 µs of CPU per small call, which hid the wait.
The metric counts only the client process's CPU time, so the blocked time does not affect `ops/CPU-sec`.

The hosts used a newer Amazon Linux 2023 image than the baseline run: Corretto 25.0.4.1+10-LTS instead of 25.0.4.1+8-LTS, and kernel 6.18.54 instead of 6.18.51.
This is a patch-level difference within the same JDK release.
