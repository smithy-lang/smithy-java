#!/usr/bin/env python3
"""Run the smithy-java e2e benchmarks over the real HTTP transport and compare them with the in-process stub.

For each benchmark id this exports the benchmark's response fixture from the e2e jar, and for each mode runs the
e2e jar `--runs` times in a randomized, balanced order so drift affects every mode alike. Each run is an
independent JVM at the cross-SDK settings (auto warmup, -Xbatch). The `http`/`https` modes start the Java fixture
server (benchmarks/e2e-benchmarks src/fixtureServer: platform thread per connection, BoringSSL TLS) on a free
loopback port and point the client at it; the `stub` mode replaces the transport in-process and needs no server.
Server CPU is read from the server process's rusage when it exits, covering every request it saw.

Gradle runs this through `:benchmarks:e2e-benchmarks:transportBenchmark`; the metal runner stages the same two
jars and this script to a pinned bare-metal host. Outputs under --outdir/<label>-<timestamp>/:
  raw/<benchmark>/<mode>/run<i>.json   the e2e jar's results files
  fixtures/                            exported fixtures and the server arguments used
  summary.json, summary.md             per-mode statistics and ratios against the stub
"""

import argparse
import json
import math
import os
import platform
import random
import shlex
import signal
import subprocess
import sys
import time
from typing import Dict, List, Optional, Tuple

HERE = os.path.dirname(os.path.abspath(__file__))
MODULE_DIR = os.path.dirname(HERE)
REPO_ROOT = os.path.dirname(os.path.dirname(MODULE_DIR))
DEFAULT_JAR = os.path.join(MODULE_DIR, "build", "libs", "smithy-java-e2e-benchmark.jar")
DEFAULT_SERVER_JAR = os.path.join(MODULE_DIR, "build", "libs", "smithy-java-fixture-server.jar")
DEFAULT_PILOT = [
    "rpcv2Cbor_PutItemRequest_Baseline",   # tiny request and response
    "awsJson1_0_GetItemOutput_M",          # medium structured response
    "restXml_PutObject_L",                 # large upload
    "restXml_GetObject_L",                 # large streaming download
]
# The server allocates per connection, not per request, so a small single-threaded heap keeps the JVM's own
# threads out of the way on a pinned core; -Xbatch keeps compilation off the serving thread once warm.
SERVER_JVM_FLAGS = ["-Xbatch", "-XX:+UseSerialGC", "-Xms64m", "-Xmx64m", "-XX:+AlwaysPreTouch",
                    "-Dio.netty.leakDetection.level=disabled"]
# Two-sided 95% t critical values by degrees of freedom; 1.96 beyond the table.
T95 = {1: 12.706, 2: 4.303, 3: 3.182, 4: 2.776, 5: 2.571, 6: 2.447, 7: 2.365, 8: 2.306, 9: 2.262,
       10: 2.228, 11: 2.201, 12: 2.179, 13: 2.160, 14: 2.145, 15: 2.131, 16: 2.120, 17: 2.110,
       18: 2.101, 19: 2.093, 20: 2.086, 25: 2.060, 30: 2.042}


def log(message: str) -> None:
    print("[%s] %s" % (time.strftime("%H:%M:%S"), message), flush=True)


def t95(df: int) -> float:
    if df <= 0:
        return float("nan")
    if df in T95:
        return T95[df]
    for k in sorted(T95):
        if df < k:
            return T95[k]
    return 1.96


def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jar", default=DEFAULT_JAR, help="e2e benchmark jar (default: the module's build output)")
    parser.add_argument("--server-jar", default=DEFAULT_SERVER_JAR,
                        help="fixture server jar for the http/https modes (default: the module's build output)")
    parser.add_argument("--java", default=os.path.join(os.environ["JAVA_HOME"], "bin", "java")
                        if os.environ.get("JAVA_HOME") else "java")
    parser.add_argument("--benchmarks", default=",".join(DEFAULT_PILOT), help="comma-separated benchmark ids")
    parser.add_argument("--modes", default="stub,https", help="comma-separated: stub, http, https")
    parser.add_argument("--runs", type=int, default=3, help="independent runs per mode")
    parser.add_argument("--e2e-args", default="", help="extra arguments for every e2e run")
    parser.add_argument("--label", default=platform.node().split(".")[0] or "host", help="host label in the results")
    parser.add_argument("--outdir", default=os.path.join(MODULE_DIR, "build", "transport-benchmark"))
    parser.add_argument("--cert", default=os.path.join(HERE, "certs", "server.pem"))
    parser.add_argument("--key", default=os.path.join(HERE, "certs", "server-key.pem"))
    parser.add_argument("--make-cert", default=os.path.join(HERE, "make-cert.sh"),
                        help="script that writes server.pem/server-key.pem when https is requested and they are absent")
    parser.add_argument("--seed", type=int, default=20261009, help="seed for the balanced run ordering")
    parser.add_argument("--imds", action="store_true", help="let the e2e jar record the EC2 instance type from IMDS")
    parser.add_argument("--server-taskset", default="", help="Linux: pin the fixture server to these CPUs, e.g. 2")
    parser.add_argument("--client-taskset", default="", help="Linux: pin the client JVMs to these CPUs, e.g. 4-11")
    args = parser.parse_args(argv)
    args.benchmark_ids = [b.strip() for b in args.benchmarks.split(",") if b.strip()]
    args.mode_list = [m.strip() for m in args.modes.split(",") if m.strip()]
    if not args.benchmark_ids or not args.mode_list:
        parser.error("--benchmarks and --modes must not be empty")
    if len(set(args.mode_list)) != len(args.mode_list):
        parser.error("--modes must not contain duplicates")
    for mode in args.mode_list:
        if mode not in ("stub", "http", "https"):
            parser.error("unknown mode %s; expected stub, http or https" % mode)
    if args.runs < 1:
        parser.error("--runs must be at least 1")
    return args


class FixtureServer:
    """One fixture server process, started on a free port, stopped with SIGTERM, CPU read via wait4."""

    def __init__(self, mode: str, fixture: dict, args: argparse.Namespace):
        command = [args.java] + SERVER_JVM_FLAGS + ["-jar", args.server_jar, "--listen", "127.0.0.1:0"]
        command += list(fixture["server_args"])  # the body path is absolute in the fixture description
        if mode == "https":
            command += ["--cert", args.cert, "--key", args.key, "--tls-version", "1.3"]
        else:
            command += ["--plaintext"]
        if args.server_taskset:
            command = ["taskset", "-c", args.server_taskset] + command
        self.command = command
        self.process = subprocess.Popen(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        line = self.process.stdout.readline()
        while line and not line.startswith("Listening on "):
            log("   server: " + line.strip())
            line = self.process.stdout.readline()
        if not line.startswith("Listening on "):
            self.stop()
            raise RuntimeError("%s server failed to start (exit %s)" % (mode, self.process.returncode))
        self.url = line.split()[2]
        self.cpu_seconds: Optional[float] = None

    def stop(self) -> None:
        if self.process.returncode is not None:
            return
        # Popen.poll() reaps an exited child, losing the rusage that wait4 needs.
        try:
            os.kill(self.process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        _, status, rusage = os.wait4(self.process.pid, 0)
        self.process.returncode = os.waitstatus_to_exitcode(status)
        self.cpu_seconds = rusage.ru_utime + rusage.ru_stime
        self.process.stdout.close()


def export_fixture(args: argparse.Namespace, benchmark: str, fixtures_dir: str) -> dict:
    subprocess.run([args.java, "-jar", args.jar, "export-fixture", benchmark, "--out", fixtures_dir],
                   check=True, stdout=subprocess.DEVNULL)
    with open(os.path.join(fixtures_dir, benchmark + ".fixture.json"), encoding="utf-8") as handle:
        return json.load(handle)


def run_once(args: argparse.Namespace, benchmark: str, protocol: str, mode: str, server: Optional[FixtureServer],
             output: str) -> Tuple[Optional[dict], str]:
    command = [args.java, "-Xbatch", "-jar", args.jar, "--protocol", protocol, "--filter", benchmark, "--output", output,
               "--notes", "transport benchmark, mode %s" % mode, "--in-process"]
    if not args.imds:
        command += ["--instance-type", args.label]
    if server is None:
        command += ["--transport", "stub"]
    else:
        command += ["--transport", mode, "--endpoint", server.url]
    if args.client_taskset:
        command = ["taskset", "-c", args.client_taskset] + command
    if args.e2e_args:
        command += shlex.split(args.e2e_args)
    completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if completed.returncode != 0 or not os.path.isfile(output):
        return None, completed.stdout[-4000:]
    with open(output, encoding="utf-8") as handle:
        report = json.load(handle)
    entries = report["benchmarks"]
    if len(entries) != 1 or entries[0]["id"] != benchmark:
        return None, "expected one benchmark (%s) in %s, got %s" % (benchmark, output, [b["id"] for b in entries])
    return report, completed.stdout


def stats(values: List[float]) -> dict:
    n = len(values)
    if n == 0:
        return {"n": 0}
    mean = sum(values) / n
    sd = math.sqrt(sum((v - mean) ** 2 for v in values) / (n - 1)) if n > 1 else 0.0
    ordered = sorted(values)
    median = ordered[n // 2] if n % 2 else (ordered[n // 2 - 1] + ordered[n // 2]) / 2
    half = t95(n - 1) * sd / math.sqrt(n) if n > 1 else float("nan")
    return {"n": n, "median": median, "mean": mean, "sd": sd,
            "cv_pct": (sd / mean * 100) if mean else float("nan"),
            "ci95_low": mean - half if n > 1 else None, "ci95_high": mean + half if n > 1 else None,
            "min": ordered[0], "max": ordered[-1]}


def ratio_with_ci(a: dict, b: dict) -> Optional[dict]:
    """b / a with a delta-method 95% interval; None when either side lacks two samples."""
    if a.get("n", 0) < 2 or b.get("n", 0) < 2 or not a["mean"] or not b["mean"]:
        return None
    ratio = b["mean"] / a["mean"]
    rel_var = (a["sd"] ** 2) / (a["n"] * a["mean"] ** 2) + (b["sd"] ** 2) / (b["n"] * b["mean"] ** 2)
    half = t95(min(a["n"], b["n"]) - 1) * ratio * math.sqrt(rel_var)
    return {"ratio": ratio, "ci95_low": ratio - half, "ci95_high": ratio + half, "method": "delta method, approximate"}


def fmt(value: Optional[float], digits: int = 1) -> str:
    if value is None or (isinstance(value, float) and math.isnan(value)):
        return "n/a"
    return ("{:,.%df}" % digits).format(value)


def ensure_cert(args: argparse.Namespace) -> None:
    if "https" not in args.mode_list:
        return
    if os.path.isfile(args.cert) and os.path.isfile(args.key):
        return
    cert_dir = os.path.dirname(args.cert) or "."
    log("generating a self-signed certificate in %s" % cert_dir)
    subprocess.run(["sh", args.make_cert, cert_dir], check=True, stdout=subprocess.DEVNULL)


def main(argv: List[str]) -> int:
    args = parse_args(argv)
    if not os.path.isfile(args.jar):
        sys.exit("error: e2e jar not found at %s (build :benchmarks:e2e-benchmarks:shadowJar)" % args.jar)
    network_modes = [m for m in args.mode_list if m != "stub"]
    if network_modes and not os.path.isfile(args.server_jar):
        sys.exit("error: fixture server jar not found at %s (build :benchmarks:e2e-benchmarks:fixtureServerJar)"
                 % args.server_jar)
    ensure_cert(args)

    run_dir = os.path.join(args.outdir, "%s-%s" % (args.label, time.strftime("%Y%m%d-%H%M%S")))
    fixtures_dir = os.path.join(run_dir, "fixtures")
    os.makedirs(fixtures_dir, exist_ok=True)
    rng = random.Random(args.seed)
    results: Dict[str, Dict[str, dict]] = {}
    failures: List[str] = []
    started = time.time()

    for benchmark in args.benchmark_ids:
        log("== %s" % benchmark)
        fixture = export_fixture(args, benchmark, fixtures_dir)
        protocol = fixture["protocol"]
        log("   fixture: status %s, %s body bytes, request body ~%s bytes" % (
            fixture["status"], fixture["body_bytes"], fixture["request_body_bytes"]))
        servers: Dict[str, FixtureServer] = {}
        incomplete_modes = set()
        per_mode: Dict[str, List[dict]] = {m: [] for m in args.mode_list}
        ops_seen: Dict[str, int] = {m: 0 for m in args.mode_list}
        try:
            for mode in network_modes:
                servers[mode] = FixtureServer(mode, fixture, args)
                log("   %s on %s" % (mode, servers[mode].url))
            for i in range(1, args.runs + 1):
                order = list(args.mode_list)
                rng.shuffle(order)
                for mode in order:
                    out_dir = os.path.join(run_dir, "raw", benchmark, mode)
                    os.makedirs(out_dir, exist_ok=True)
                    output = os.path.join(out_dir, "run%d.json" % i)
                    report, console = run_once(args, benchmark, protocol, mode, servers.get(mode), output)
                    if report is None:
                        incomplete_modes.add(mode)
                        failures.append("%s/%s run %d" % (benchmark, mode, i))
                        log("   FAIL %-6s run %d\n%s" % (mode, i, console))
                        continue
                    entry = report["benchmarks"][0]
                    per_mode[mode].append(entry)
                    if not entry["verification"]["http_requests_match_iterations"]:
                        incomplete_modes.add(mode)
                    ops_seen[mode] += entry["iterations"] + entry["warmup"]["iterations"] + 1
                    log("   %-6s run %d: %s ops/CPU-sec, %.2f us/op, cpu/wall %.3f, %s" % (
                        mode, i, fmt(entry["ops_per_cpu_sec"], 0), 1e6 / entry["ops_per_cpu_sec"],
                        entry["cpu_wall_ratio"], entry["verification"].get("http_version", "stub")))
        finally:
            for mode, server in servers.items():
                server.stop()
                log("   %s stopped: %.2f s CPU over %d requests" % (mode, server.cpu_seconds, ops_seen.get(mode, 0)))

        results[benchmark] = {}
        for mode in args.mode_list:
            entries = per_mode[mode]
            ops = [e["ops_per_cpu_sec"] for e in entries]
            summary = {
                "mode": mode,
                "runs": len(entries),
                "ops_per_cpu_sec": stats(ops),
                "client_cpu_us_per_op": stats([1e6 / v for v in ops]),
                "throughput_ops_per_wall_sec": stats([e["ops_per_wall_sec"] for e in entries]),
                "cpu_wall_ratio": stats([e["cpu_wall_ratio"] for e in entries]),
                "under_warmed_runs": sum(1 for e in entries if e["verification"]["under_warmed"]),
                "http_version": sorted({e["verification"].get("http_version", "stub") for e in entries}),
            }
            server = servers.get(mode)
            if server is not None and server.cpu_seconds is not None and ops_seen[mode] and mode not in incomplete_modes:
                summary["server"] = {
                    "command": server.command,
                    "cpu_seconds_total": server.cpu_seconds,
                    "requests_total_including_warmup": ops_seen[mode],
                    "server_cpu_us_per_op": 1e6 * server.cpu_seconds / ops_seen[mode],
                }
            results[benchmark][mode] = summary
        stub = results[benchmark].get("stub")
        if stub:
            for mode, summary in results[benchmark].items():
                if mode != "stub":
                    summary["vs_stub"] = {
                        "client_cpu_us_per_op_ratio": ratio_with_ci(stub["client_cpu_us_per_op"],
                                                                    summary["client_cpu_us_per_op"]),
                        "throughput_ratio": ratio_with_ci(stub["throughput_ops_per_wall_sec"],
                                                           summary["throughput_ops_per_wall_sec"]),
                    }

    metadata = {
        "label": args.label,
        "platform": platform.platform(),
        "machine": platform.machine(),
        "cpu_count": os.cpu_count(),
        "jar": args.jar,
        "server_jar": args.server_jar,
        "server_jvm_flags": SERVER_JVM_FLAGS,
        "server_taskset": args.server_taskset,
        "client_taskset": args.client_taskset,
        "e2e_args": args.e2e_args,
        "runs_per_mode": args.runs,
        "modes": args.mode_list,
        "benchmarks": args.benchmark_ids,
        "seed": args.seed,
        "duration_seconds": round(time.time() - started, 1),
        "failures": failures,
        "smithy_java_commit": git_commit(REPO_ROOT),
        "note": "Development or pilot run unless the label says otherwise; final numbers need an idle Linux host "
                "with pinned frequency and client/server on disjoint physical cores.",
    }
    with open(os.path.join(run_dir, "summary.json"), "w", encoding="utf-8") as handle:
        json.dump({"metadata": metadata, "results": results}, handle, indent=2)
    markdown = render_markdown(metadata, results)
    with open(os.path.join(run_dir, "summary.md"), "w", encoding="utf-8") as handle:
        handle.write(markdown)
    print()
    print(markdown)
    log("results: %s" % run_dir)
    return 1 if failures else 0


def git_commit(root: str) -> str:
    try:
        return subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True,
                              check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def render_markdown(metadata: dict, results: Dict[str, Dict[str, dict]]) -> str:
    lines = ["# e2e transport benchmark: %s" % metadata["label"], ""]
    lines.append("%s, %d cpus, %d run(s) per mode, smithy-java %s" % (
        metadata["platform"], metadata["cpu_count"] or 0, metadata["runs_per_mode"],
        metadata["smithy_java_commit"][:12]))
    lines.append("")
    lines.append("_%s_" % metadata["note"])
    lines.append("")
    for benchmark, modes in results.items():
        lines.append("## %s" % benchmark)
        lines.append("")
        lines.append("| mode | runs | client us/op mean | CV | 95% CI | vs stub | throughput ops/s | cpu/wall | http | server us/op |")
        lines.append("|---|---:|---:|---:|---|---|---:|---:|---|---:|")
        for mode, s in modes.items():
            c = s["client_cpu_us_per_op"]
            if c.get("n", 0) == 0:
                lines.append("| %s | 0 | failed | | | | | | | |" % mode)
                continue
            ci = "%s to %s" % (fmt(c.get("ci95_low"), 2), fmt(c.get("ci95_high"), 2)) if c.get("ci95_low") is not None else "n/a"
            vs = s.get("vs_stub", {}).get("client_cpu_us_per_op_ratio")
            vs_text = "%.2fx (%.2f to %.2f)" % (vs["ratio"], vs["ci95_low"], vs["ci95_high"]) if vs else ("1.00x" if mode == "stub" else "n/a")
            server = s.get("server", {}).get("server_cpu_us_per_op")
            lines.append("| %s | %d | %s | %s%% | %s | %s | %s | %s | %s | %s |" % (
                mode, c["n"], fmt(c["mean"], 2), fmt(c["cv_pct"], 1), ci, vs_text,
                fmt(s["throughput_ops_per_wall_sec"]["mean"], 0), fmt(s["cpu_wall_ratio"]["mean"], 3),
                "/".join(s["http_version"]), fmt(server, 2) if server is not None else "n/a"))
        lines.append("")
    if metadata["failures"]:
        lines.append("Failures: " + ", ".join(metadata["failures"]))
        lines.append("")
    return "\n".join(lines)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
