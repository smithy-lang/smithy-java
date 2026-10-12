#!/usr/bin/env python3
"""Compare baseline and current benchmark runs using medians per benchmark.

Input files require a nonempty `benchmarks` list with `id` and `ops_per_cpu_sec`.
The report compares shared benchmark ids and records metadata and workload differences as warnings.
Writes <out>.json with metadata, benchmarks, and summary, and prints the summary.

    python3 scripts/compare-ocs.py --baseline runs/baseline/*.json --current runs/current/*.json \\
        --out results/smithy-java/m7imetal24xl_ocs_results
"""

import argparse
import json
import math
import os
import statistics
import sys
from typing import Dict, List, Optional

SCHEMA = "smithy-java/e2e-ops-cpusec/2"
PROTOCOL_ORDER = ["AwsJson10", "RpcV2Cbor", "AwsQuery", "RestJson1", "RestXml"]
# Benchmark id prefixes of the shared model, for files that do not label their benchmarks with a protocol.
ID_PREFIXES = {"awsJson1_0": "AwsJson10", "rpcv2Cbor": "RpcV2Cbor", "awsQuery": "AwsQuery",
               "restJson1": "RestJson1", "restXml": "RestXml"}
COMPARED_METADATA = [
    ("mode", "modes"),
    ("transport", "transports"),
    ("stop_condition", "stop conditions"),
    ("measurement", "CPU time measurements"),
    ("min_iterations", "iteration floors"),
    ("check_interval", "check intervals"),
    ("client", "client modes"),
    ("instance", "instance types"),
    ("sdk", "SDKs"),
]


class Side:
    """One set of run files: the metadata of the first, and every benchmark's samples across all of them."""

    def __init__(self, label: str, paths: List[str]):
        self.label = label
        self.paths = paths
        self.warnings: List[str] = []
        runs = [self.load(p) for p in paths]
        self.metadata: dict = runs[0].get("metadata", {})
        for path, run in zip(paths[1:], runs[1:]):
            for key in ("mode", "commit", "stop_condition", "instance", "sdk", "sdk_version"):
                mine, theirs = self.metadata.get(key), run.get("metadata", {}).get(key)
                if mine != theirs:
                    self.warnings.append("%s samples differ in %s: %s has %r, %s has %r"
                                         % (label, key, os.path.basename(paths[0]), mine, os.path.basename(path), theirs))
        self.order: List[str] = []
        self.samples: Dict[str, List[float]] = {}
        self.protocol: Dict[str, str] = {}
        self.fingerprint: Dict[str, Optional[str]] = {}
        self.warmup_iterations: List[int] = []
        self.cpu_wall_ratios: List[float] = []
        self.under_warmed = 0
        for run in runs:
            for b in run["benchmarks"]:
                benchmark_id = b["id"]
                if benchmark_id not in self.samples:
                    self.order.append(benchmark_id)
                self.samples.setdefault(benchmark_id, []).append(float(b["ops_per_cpu_sec"]))
                self.protocol[benchmark_id] = b.get("protocol")
                self.fingerprint[benchmark_id] = b.get("workload_fingerprint")
                if isinstance(b.get("warmup"), dict) and "iterations" in b["warmup"]:
                    self.warmup_iterations.append(int(b["warmup"]["iterations"]))
                if "cpu_wall_ratio" in b:
                    self.cpu_wall_ratios.append(float(b["cpu_wall_ratio"]))
                self.under_warmed += 1 if b.get("under_warmed") else 0
        counts = sorted({len(v) for v in self.samples.values()})
        if len(counts) != 1:
            self.warnings.append("%s benchmarks have between %d and %d samples; not every run covered the same cases"
                                 % (label, counts[0], counts[-1]))
        self.samples_per_benchmark = counts[-1]
        if self.samples_per_benchmark < 3:
            self.warnings.append("%s has %d sample(s) per benchmark; at least 3 are expected"
                                 % (label.capitalize(), self.samples_per_benchmark))
        if not self.metadata.get("background_jit_compilation_disabled", False):
            self.warnings.append("%s was measured without -Xbatch (or does not record it)" % label.capitalize())
        if self.under_warmed:
            self.warnings.append("%d under-warmed %s sample(s)" % (self.under_warmed, label))

    def load(self, path: str) -> dict:
        with open(path, encoding="utf-8") as handle:
            run = json.load(handle)
        if not isinstance(run.get("benchmarks"), list) or not run["benchmarks"]:
            fail("%s has no benchmarks list" % path)
        for b in run["benchmarks"]:
            if "id" not in b or "ops_per_cpu_sec" not in b:
                fail("%s: every benchmark needs an id and ops_per_cpu_sec" % path)
        if run.get("schema") != SCHEMA:
            self.warnings.append("%s is not a %s file (schema %r); metadata may be incomplete"
                                 % (os.path.basename(path), SCHEMA, run.get("schema")))
        return run

    def median(self, benchmark_id: str) -> float:
        return statistics.median(self.samples[benchmark_id])

    def environment(self, key: str):
        return self.metadata.get("environment", {}).get(key)

    def java_version(self) -> Optional[str]:
        version = self.metadata.get("environment", {}).get("java", {}).get("version")
        return str(version) if version is not None else None

    def date(self) -> str:
        return str(self.metadata.get("run_started_utc", ""))[:10]

    def cpu_wall_ratio(self) -> Optional[float]:
        return round(statistics.mean(self.cpu_wall_ratios), 3) if self.cpu_wall_ratios else None

    def describe(self) -> dict:
        return {
            "date": self.date(),
            "commit": self.metadata.get("commit"),
            "branch": self.metadata.get("branch"),
            "sdk": self.metadata.get("sdk"),
            "sdk_version": self.metadata.get("sdk_version"),
            "mode": self.metadata.get("mode"),
            "transport": self.metadata.get("transport"),
            "stop_condition": self.metadata.get("stop_condition"),
            "instance": self.metadata.get("instance"),
            "java": self.java_version(),
            "cpu": self.environment("cpu"),
            "samples": self.samples_per_benchmark,
            "cpu_wall_ratio": self.cpu_wall_ratio(),
            "files": [os.path.basename(p) for p in self.paths],
        }


def fail(message: str) -> None:
    raise SystemExit("error: " + message)


def protocol_of(benchmark_id: str, *labels: Optional[str]) -> str:
    """The first explicit protocol label, else the shared model's id prefix, else the id's first token."""
    for label in labels:
        if label:
            return label
    for prefix, name in ID_PREFIXES.items():
        if benchmark_id.startswith(prefix + "_"):
            return name
    return benchmark_id.split("_", 1)[0]


def geomean(values: List[float]) -> float:
    return math.exp(sum(math.log(v) for v in values) / len(values))


def improvement_pct(baseline: float, current: float) -> float:
    return round((current / baseline - 1) * 100, 2)


def compare(baseline: Side, current: Side, lang: str) -> dict:
    warnings: List[str] = []
    for key, what in COMPARED_METADATA:
        b, c = baseline.metadata.get(key), current.metadata.get(key)
        if b != c:
            warnings.append("Different %s: baseline %r, current %r" % (what, b, c))
    if baseline.java_version() != current.java_version():
        warnings.append("Different JVMs: baseline %r, current %r" % (baseline.java_version(), current.java_version()))
    if baseline.environment("cpu") != current.environment("cpu"):
        warnings.append("Different CPUs: baseline %r, current %r" % (baseline.environment("cpu"), current.environment("cpu")))
    warnings += baseline.warnings + current.warnings

    common = [i for i in current.order if i in baseline.samples]
    if not common:
        fail("the two sides have no benchmark ids in common")
    only_b = sorted(set(baseline.samples) - set(current.samples))
    only_c = sorted(set(current.samples) - set(baseline.samples))
    if only_b or only_c:
        warnings.append("Benchmark sets differ: %d only in baseline, %d only in current; compared the %d in common"
                        % (len(only_b), len(only_c), len(common)))
    changed = [i for i in common
               if baseline.fingerprint[i] and current.fingerprint[i] and baseline.fingerprint[i] != current.fingerprint[i]]
    if changed:
        warnings.append("Workload fingerprints differ for %d benchmark(s), so inputs or responses are not identical: %s"
                        % (len(changed), ", ".join(changed[:5]) + (", ..." if len(changed) > 5 else "")))

    benchmarks = []
    by_protocol: Dict[str, List[List[float]]] = {}
    for benchmark_id in common:
        b = baseline.median(benchmark_id)
        c = current.median(benchmark_id)
        benchmarks.append({"id": benchmark_id, "baseline": round(b), "current": round(c),
                           "improvement_pct": improvement_pct(b, c)})
        protocol = protocol_of(benchmark_id, current.protocol[benchmark_id], baseline.protocol[benchmark_id])
        pair = by_protocol.setdefault(protocol, [[], []])
        pair[0].append(b)
        pair[1].append(c)

    protocols = {}
    for name in PROTOCOL_ORDER + sorted(set(by_protocol) - set(PROTOCOL_ORDER)):
        if name not in by_protocol:
            continue
        b_values, c_values = by_protocol[name]
        protocols[name] = {"baseline": round(geomean(b_values)), "current": round(geomean(c_values)),
                           "improvement_pct": improvement_pct(geomean(b_values), geomean(c_values)),
                           "benchmark_count": len(b_values)}
    all_b = [baseline.median(i) for i in common]
    all_c = [current.median(i) for i in common]
    overall = {"baseline": round(geomean(all_b)), "current": round(geomean(all_c)),
               "improvement_pct": improvement_pct(geomean(all_b), geomean(all_c)),
               "benchmark_count": len(common), "aggregation": "geometric_mean"}

    meta = current.metadata
    metadata = {
        "lang": lang,
        "instance": meta.get("instance"),
        "region": meta.get("region"),
        "metric": meta.get("metric", "ops_per_cpu_second"),
        "measurement": meta.get("measurement"),
        "stop_condition": meta.get("stop_condition"),
        # Warmup is adaptive per benchmark; this is the smallest count any current-side benchmark received.
        "warmup_iterations": min(current.warmup_iterations) if current.warmup_iterations else None,
        "warmup_policy": meta.get("warmup_policy_detail"),
        "http_mock": meta.get("http_mock"),
        "mode": meta.get("mode"),
        "software": [["Java", current.java_version()], [meta.get("sdk", lang), meta.get("sdk_version")]],
        "os": current.environment("os_label"),
        "cpu": current.environment("cpu"),
        "baseline_date": baseline.date(),
        "baseline_commit": baseline.metadata.get("commit"),
        "baseline_branch": baseline.metadata.get("branch"),
        "current_date": current.date(),
        "current_commit": meta.get("commit"),
        "current_branch": meta.get("branch"),
        "sides": {"baseline": baseline.describe(), "current": current.describe()},
        "comparability_warnings": warnings,
    }
    return {"metadata": metadata, "benchmarks": benchmarks, "summary": {"protocols": protocols, "overall": overall}}


def report(result: dict) -> str:
    meta = result["metadata"]
    sides = meta["sides"]
    overall = result["summary"]["overall"]
    lines = [
        "SDK:          %s" % meta["lang"],
        "Architecture: %s" % meta["os"],
        "Instance:     %s" % both(sides, "instance"),
        "Mode:         %s" % both(sides, "mode"),
        "Date:         %s (commit %s) vs %s (commit %s)" % (sides["baseline"]["date"], sides["baseline"]["commit"],
                                                            sides["current"]["date"], sides["current"]["commit"]),
        "Samples:      %d baseline, %d current (medians per benchmark)"
        % (sides["baseline"]["samples"], sides["current"]["samples"]),
        "",
        "Per-Protocol Geometric Mean (ops/CPU-sec):",
        "  %-12s %10s %10s %10s" % ("Protocol", "Baseline", "Current", "Delta %"),
    ]
    for name, p in result["summary"]["protocols"].items():
        lines.append("  %-12s %10s %10s %10s" % (name, fmt(p["baseline"]), fmt(p["current"]), pct(p["improvement_pct"])))
    reduction = (1 - overall["baseline"] / overall["current"]) * 100
    lines += [
        "",
        "Overall Geometric Mean (%d benchmarks):" % overall["benchmark_count"],
        "  Baseline:     %s ops/CPU-sec" % fmt(overall["baseline"]),
        "  Current:      %s ops/CPU-sec" % fmt(overall["current"]),
        "  Delta:        %s" % pct(overall["improvement_pct"]),
        "",
        "CPU Time Reduction:",
        "  Formula:      1 - (baseline_ops_cpu_sec / current_ops_cpu_sec)",
        "  Result:       %.1f%% %s CPU time per operation" % (abs(reduction), "less" if reduction >= 0 else "more"),
        "",
        "CPU/Wall Ratio: baseline %s, current %s (expect 0.95-1.05)"
        % (ratio(sides["baseline"]["cpu_wall_ratio"]), ratio(sides["current"]["cpu_wall_ratio"])),
    ]
    if meta["comparability_warnings"]:
        lines.append("")
        lines.append("Comparability warnings:")
        lines += ["  - " + w for w in meta["comparability_warnings"]]
    return "\n".join(lines) + "\n"


def both(sides: dict, key: str) -> str:
    b, c = sides["baseline"].get(key), sides["current"].get(key)
    return str(c) if b == c else "%s (baseline) vs %s (current)" % (b, c)


def ratio(value: Optional[float]) -> str:
    return "%.3f" % value if value is not None else "n/a"


def fmt(value: float) -> str:
    return "{:,}".format(round(value))


def pct(value: float) -> str:
    return ("+" if value >= 0 else "-") + ("%g" % abs(value)) + "%"


def main(argv: List[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--baseline", nargs="+", required=True, metavar="RUN.json", help="run file(s) of the baseline side")
    parser.add_argument("--current", nargs="+", required=True, metavar="RUN.json", help="run file(s) of the current side")
    parser.add_argument("--out", required=True, help="output prefix; <out>.json is written")
    parser.add_argument("--lang", default="smithy-java", help="SDK label in the results (default %(default)s)")
    args = parser.parse_args(argv)

    result = compare(Side("baseline", args.baseline), Side("current", args.current), args.lang)
    out = args.out[:-5] if args.out.endswith(".json") else args.out
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    with open(out + ".json", "w", encoding="utf-8") as handle:
        json.dump(result, handle, indent=4)
        handle.write("\n")
    sys.stdout.write(report(result))
    print("Wrote %s.json" % out)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
