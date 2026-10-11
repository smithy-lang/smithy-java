# Benchmark results

These files contain results from the end-to-end client benchmark in `benchmarks/e2e-benchmarks`.
It measures the full client pipeline: serialization, endpoint resolution, SigV4 signing, retry handling, interceptors, and deserialization.

The benchmark ran on bare-metal EC2 hosts.
The metric is operations per CPU second (`ops/CPU-sec`).

Both modes measure the same client calls:

- `stub` uses a transport that returns prepared responses within the process, without sockets.
- `https` sends requests to a real HTTPS test server on loopback.

Each JSON file contains results from one JVM run of the 71 canonical cases.
The files use the schema `smithy-java/e2e-ops-cpusec/2`.

The files use this directory structure:

```
benchmark-results/<run>/<instance-type>/<mode>/sample<N>.json
```

| Run | SDK | Hosts | Modes | Date |
|---|---|---|---|---|
| [e2e-baseline-20260211](e2e-baseline-20260211/README.md) | 0.0.3 (Feb 2026 baseline) | m7i.metal-24xl, m7g.metal | stub, https | 2026-10-10 |
