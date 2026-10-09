#!/usr/bin/env python3
"""Single scheduler for the smithy-java e2e ops/CPU-sec matrix: side x case x transport x sample.

One tool runs the whole matrix, locally and on a pinned bare-metal host, driven by a JSON run manifest
(``--manifest``) instead of positional shell arguments reconstructed with ``eval``. It replaces both the
old per-benchmark transport driver and the e2e sample loop that used to live in the host-side runner.

It does *no* statistics of its own. Every run writes the e2e jar's own results file; comparison, sample
acceptance, aggregation and uncertainty are the job of the one implementation, the jar's ``compare``
subcommand (CompareRuns), which this script invokes for each requested pair.

Two measurement shapes, both expressed as "cases" in the manifest:
  * ``group: all``  - the canonical submission run: the whole benchmark set in one jar invocation that forks
    one -Xbatch child JVM per protocol. Transport must be ``stub`` (the in-process mock); no server.
  * ``id: <bench>`` - the transport study: one benchmark per JVM (``--in-process --filter``), over ``stub``
    and/or ``https``. Non-stub transports start the Java fixture server (platform thread per connection,
    BoringSSL TLS) on a free loopback port for that one benchmark's canned response.

Raw layout under ``<outdir>/raw/<slot>/<transport>/<side>/<key>.run<i>.json`` so that each (slot, transport,
side) directory is exactly one comparable set for ``compare``. ``key`` is the benchmark id, or ``all`` for the
canonical group. Run bookkeeping (which runs ran, failures, timing) goes to ``run-metadata.json``; the ocs comparison files are
written where each manifest ``compare`` entry asks.

Without ``--manifest`` the command-line flags build an equivalent manifest for a local transport study, so
``./gradlew :benchmarks:e2e-benchmarks:transportBenchmark`` and ad-hoc local runs keep working.
"""

import argparse
import json
import os
import platform
import random
import shlex
import subprocess
import sys
import time
from types import SimpleNamespace
from typing import Dict, List, Optional, Tuple

HERE = os.path.dirname(os.path.abspath(__file__))
MODULE_DIR = os.path.dirname(HERE)
REPO_ROOT = os.path.dirname(os.path.dirname(MODULE_DIR))
DEFAULT_JAR = os.path.join(MODULE_DIR, "build", "libs", "smithy-java-e2e-benchmark.jar")
DEFAULT_SERVER_JAR = os.path.join(MODULE_DIR, "build", "libs", "smithy-java-fixture-server.jar")
MANIFEST_SCHEMA = "smithy-java/e2e-run-manifest/1"
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


def log(message: str) -> None:
    print("[%s] %s" % (time.strftime("%H:%M:%S"), message), flush=True)


# ---------------------------------------------------------------------------------------------------------
# Command-line interface (local transport study) -> manifest
# ---------------------------------------------------------------------------------------------------------
def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--manifest", default=None,
                        help="run from this JSON manifest; the flags below are ignored except --java")
    parser.add_argument("--jar", default=DEFAULT_JAR, help="e2e benchmark jar (default: the module's build output)")
    parser.add_argument("--baseline-jar", default=None,
                        help="also measure this older e2e jar as the 'baseline' side")
    parser.add_argument("--server-jar", default=DEFAULT_SERVER_JAR,
                        help="fixture server jar for the http/https modes (default: the module's build output)")
    parser.add_argument("--java", default=os.path.join(os.environ["JAVA_HOME"], "bin", "java")
                        if os.environ.get("JAVA_HOME") else "java")
    parser.add_argument("--benchmarks", default=",".join(DEFAULT_PILOT), help="comma-separated benchmark ids")
    parser.add_argument("--modes", default="stub,https", help="comma-separated: stub, http, https")
    parser.add_argument("--runs", type=int, default=3, help="independent samples per side x case x transport")
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
    parser.add_argument("--lang", default="smithy-java", help="SDK label the comparison records")
    args = parser.parse_args(argv)
    args.benchmark_ids = [b.strip() for b in args.benchmarks.split(",") if b.strip()]
    args.mode_list = [m.strip() for m in args.modes.split(",") if m.strip()]
    if not args.manifest:
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


def manifest_from_args(args: argparse.Namespace) -> dict:
    """A local transport study: per-case runs over the requested modes, for current (and optional baseline)."""
    sides = [{"label": "current", "jar": os.path.abspath(args.jar)}]
    if args.baseline_jar:
        sides.insert(0, {"label": "baseline", "jar": os.path.abspath(args.baseline_jar)})
    cases = [{"slot": "cases", "id": b, "mode": "in-process", "transports": list(args.mode_list),
              "client_taskset": args.client_taskset}
             for b in args.benchmark_ids]
    compare = []
    for side in sides:
        if "stub" in args.mode_list and "https" in args.mode_list:
            compare.append({"out": os.path.join(args.outdir, "%s_stub_vs_https" % side["label"]),
                            "lang": args.lang, "allow_transport_diff": True, "allow_partial": True,
                            "baseline": {"slot": "cases", "transport": "stub", "side": side["label"]},
                            "current": {"slot": "cases", "transport": "https", "side": side["label"]}})
    if len(sides) == 2:
        for transport in args.mode_list:
            compare.append({"out": os.path.join(args.outdir, "baseline_vs_current_%s" % transport),
                            "lang": args.lang,
                            "baseline": {"slot": "cases", "transport": transport, "side": "baseline"},
                            "current": {"slot": "cases", "transport": transport, "side": "current"}})
    return {
        "schema": MANIFEST_SCHEMA,
        "label": args.label,
        "seed": args.seed,
        "samples": args.runs,
        "outdir": os.path.abspath(args.outdir),
        "imds": bool(args.imds),
        "e2e_args": shlex.split(args.e2e_args) if args.e2e_args else [],
        "client_taskset": args.client_taskset,
        "server": {"jar": os.path.abspath(args.server_jar), "jvm_flags": SERVER_JVM_FLAGS,
                   "cert": args.cert, "key": args.key, "make_cert": args.make_cert,
                   "taskset": args.server_taskset},
        "sides": sides,
        "cases": cases,
        "compare": compare,
    }


# ---------------------------------------------------------------------------------------------------------
# Manifest loading and validation
# ---------------------------------------------------------------------------------------------------------
def load_manifest(path: str) -> dict:
    with open(path, encoding="utf-8") as handle:
        manifest = json.load(handle)
    validate_manifest(manifest)
    return manifest


def validate_manifest(m: dict) -> None:
    if m.get("schema") != MANIFEST_SCHEMA:
        raise ValueError("manifest schema is %r, expected %r" % (m.get("schema"), MANIFEST_SCHEMA))
    if not m.get("sides"):
        raise ValueError("manifest has no sides")
    labels = [s["label"] for s in m["sides"]]
    if len(set(labels)) != len(labels):
        raise ValueError("manifest side labels must be unique: %s" % labels)
    if int(m.get("samples", 0)) < 1:
        raise ValueError("manifest samples must be at least 1")
    if not m.get("cases"):
        raise ValueError("manifest has no cases")
    for case in m["cases"]:
        transports = case.get("transports") or []
        if not transports:
            raise ValueError("case %r has no transports" % case)
        for transport in transports:
            if transport not in ("stub", "http", "https"):
                raise ValueError("case %r has unknown transport %r" % (case, transport))
        is_group = case.get("group") == "all"
        if is_group and transports != ["stub"]:
            raise ValueError("the canonical 'all' group supports only the stub transport, not %s" % transports)
        if not is_group and not case.get("id"):
            raise ValueError("case %r must set either group:all or an id" % case)
    non_stub = any(t != "stub" for case in m["cases"] for t in case["transports"])
    if non_stub and not (m.get("server") or {}).get("jar"):
        raise ValueError("a non-stub transport is requested but manifest.server.jar is not set")


def context(manifest: dict, java: str) -> SimpleNamespace:
    """The subset of fields FixtureServer and run_once read, assembled from the manifest."""
    server = manifest.get("server") or {}
    return SimpleNamespace(
        java=java,
        server_jar=server.get("jar"),
        server_jvm_flags=server.get("jvm_flags", SERVER_JVM_FLAGS),
        cert=server.get("cert"),
        key=server.get("key"),
        make_cert=server.get("make_cert"),
        server_taskset=server.get("taskset", ""),
        client_taskset=manifest.get("client_taskset", ""),
        imds=bool(manifest.get("imds")),
        label=manifest.get("label", "host"),
        instance_type=manifest.get("instance_type"),
        e2e_args=shlex.join(manifest.get("e2e_args", [])),
        notes=manifest.get("notes", "metal run"),
    )


# ---------------------------------------------------------------------------------------------------------
# Fixture server and single runs
# ---------------------------------------------------------------------------------------------------------
class FixtureServer:
    """One fixture server process, started on a free port, stopped with SIGTERM."""

    def __init__(self, mode: str, fixture: dict, args):
        command = [args.java] + list(getattr(args, "server_jvm_flags", SERVER_JVM_FLAGS)) \
            + ["-jar", args.server_jar, "--listen", "127.0.0.1:0"]
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

    def stop(self) -> None:
        if self.process.returncode is not None:
            return
        try:
            self.process.terminate()
        except ProcessLookupError:
            pass
        try:
            self.process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()
        if self.process.stdout:
            self.process.stdout.close()


def export_fixture(ctx, jar: str, benchmark: str, fixtures_dir: str) -> dict:
    subprocess.run([ctx.java, "-jar", jar, "export-fixture", benchmark, "--out", fixtures_dir],
                   check=True, stdout=subprocess.DEVNULL)
    with open(os.path.join(fixtures_dir, benchmark + ".fixture.json"), encoding="utf-8") as handle:
        return json.load(handle)


def run_once(args, benchmark: str, protocol: str, mode: str, server: Optional["FixtureServer"],
             output: str, jar: Optional[str] = None) -> Tuple[Optional[dict], str]:
    """One per-benchmark, in-process run (the transport study). The launcher JVM is the measuring JVM, so
    -Xbatch belongs on it. Returns (report, console) or (None, console) on failure."""
    jar = jar or getattr(args, "jar", None)
    command = [args.java, "-Xbatch", "-jar", jar, "--protocol", protocol, "--filter", benchmark, "--output", output,
               "--notes", "transport benchmark, mode %s" % mode, "--in-process"]
    if not args.imds:
        command += ["--instance-type", getattr(args, "instance_type", None) or args.label]
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


def run_canonical(args, jar: str, output: str) -> Tuple[Optional[dict], str]:
    """The canonical submission run: the whole set, forking one -Xbatch child JVM per protocol (so -Xbatch
    is applied by the jar to the children, not the launcher). Stub transport, no server."""
    command = [args.java, "-jar", jar, "--output", output, "--notes", args.notes]
    if not args.imds:
        command += ["--instance-type", getattr(args, "instance_type", None) or args.label]
    if args.client_taskset:
        command = ["taskset", "-c", args.client_taskset] + command
    if args.e2e_args:
        command += shlex.split(args.e2e_args)
    completed = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    if completed.returncode != 0 or not os.path.isfile(output):
        return None, completed.stdout[-4000:]
    with open(output, encoding="utf-8") as handle:
        return json.load(handle), completed.stdout


# ---------------------------------------------------------------------------------------------------------
# The scheduler
# ---------------------------------------------------------------------------------------------------------
def ensure_cert(ctx) -> None:
    if not ctx.cert or not ctx.make_cert:
        return
    if os.path.isfile(ctx.cert) and os.path.isfile(ctx.key):
        return
    cert_dir = os.path.dirname(ctx.cert) or "."
    log("generating a self-signed certificate in %s" % cert_dir)
    subprocess.run(["sh", ctx.make_cert, cert_dir], check=True, stdout=subprocess.DEVNULL)


def raw_dir(outdir: str, slot: str, transport: str, side: str) -> str:
    return os.path.join(outdir, "raw", slot, transport, side)


def execute(manifest: dict, java: str) -> dict:
    """Run the full matrix. Returns a bookkeeping dict with the list of failed runs."""
    ctx = context(manifest, java)
    outdir = manifest["outdir"]
    fixtures_dir = os.path.join(outdir, "fixtures")
    os.makedirs(fixtures_dir, exist_ok=True)
    sides = manifest["sides"]
    samples = int(manifest["samples"])
    rng = random.Random(int(manifest.get("seed", 0)))
    export_jar = manifest.get("export_jar") or sides[-1]["jar"]
    need_https = any(t == "https" for case in manifest["cases"] for t in case["transports"])
    if need_https:
        ensure_cert(ctx)

    failures: List[str] = []

    for case in manifest["cases"]:
        slot = case.get("slot", "all" if case.get("group") == "all" else "cases")
        transports = case["transports"]
        # A case may pin its client JVMs to specific cores; the canonical all-benchmarks run is left unpinned
        # (as it historically was) by omitting client_taskset at both the case and manifest level.
        ctx.client_taskset = case.get("client_taskset", manifest.get("client_taskset", ""))
        fixture = None
        if case.get("group") == "all":
            key, protocol = "all", None
            log("== canonical (all benchmarks, fork-per-protocol), transports=%s" % transports)
        else:
            key = case["id"]
            fixture = export_fixture(ctx, export_jar, key, fixtures_dir)
            protocol = fixture["protocol"]
            log("== %s  (status %s, %s body bytes, request ~%s bytes)" % (
                key, fixture["status"], fixture["body_bytes"], fixture["request_body_bytes"]))

        servers: Dict[str, FixtureServer] = {}
        try:
            for transport in transports:
                if transport != "stub":
                    servers[transport] = FixtureServer(transport, fixture, ctx)
                    log("   %s on %s" % (transport, servers[transport].url))
            # Balanced order: shuffle the (transport, side, sample) units within the case so neither a
            # transport nor a side is systematically favoured by drift. Servers stay up for the whole case.
            units = [(t, s, i) for t in transports for s in sides for i in range(1, samples + 1)]
            rng.shuffle(units)
            for transport, side, i in units:
                out = os.path.join(raw_dir(outdir, slot, transport, side["label"]), "%s.run%d.json" % (key, i))
                os.makedirs(os.path.dirname(out), exist_ok=True)
                if case.get("group") == "all":
                    report, console = run_canonical(ctx, side["jar"], out)
                else:
                    report, console = run_once(ctx, key, protocol, transport, servers.get(transport), out,
                                               jar=side["jar"])
                tag = "%s/%s/%s run %d" % (key, transport, side["label"], i)
                if report is None:
                    failures.append(tag)
                    log("   FAIL %s\n%s" % (tag, console[-1500:]))
                    continue
                summary = report.get("summary", {})
                ops = summary.get("overall", {}).get("ops_per_cpu_sec")
                log("   ok   %s: %s ops/CPU-sec" % (tag, ("%.0f" % ops) if ops else "n/a"))
        finally:
            for server in servers.values():
                server.stop()

    return {"failures": failures}


def run_comparisons(manifest: dict, java: str, bookkeeping: dict) -> List[dict]:
    """Invoke the one comparison implementation (the jar's `compare`) for each requested pair. The scheduler
    never computes statistics; it only decides a pair is runnable (both sides produced files) and hands the
    directories to CompareRuns, which owns acceptance, aggregation and uncertainty."""
    outdir = manifest["outdir"]
    compare_jar = manifest.get("compare_jar") or manifest["sides"][-1]["jar"]
    produced: List[dict] = []
    for spec in manifest.get("compare", []):
        base_sel, cur_sel = spec["baseline"], spec["current"]
        base_dir = raw_dir(outdir, base_sel["slot"], base_sel["transport"], base_sel["side"])
        cur_dir = raw_dir(outdir, cur_sel["slot"], cur_sel["transport"], cur_sel["side"])
        label = "%s(%s/%s/%s) vs (%s/%s/%s)" % (
            spec.get("out", "compare"),
            base_sel["slot"], base_sel["transport"], base_sel["side"],
            cur_sel["slot"], cur_sel["transport"], cur_sel["side"])
        if not (_has_runs(base_dir) and _has_runs(cur_dir)):
            log("skip compare %s: a side produced no run files" % label)
            continue
        command = [java, "-jar", compare_jar, "compare", "--baseline", base_dir, "--current", cur_dir,
                   "--out", spec["out"], "--lang", spec.get("lang", "smithy-java")]
        if spec.get("allow_partial"):
            command.append("--allow-partial")
        if spec.get("allow_config_diff"):
            command.append("--allow-config-diff")
        if spec.get("allow_low_quality"):
            command.append("--allow-low-quality")
        if spec.get("allow_transport_diff"):
            command.append("--allow-transport-diff")
        log("compare %s" % label)
        completed = subprocess.run(command)
        produced.append({"out": spec["out"], "returncode": completed.returncode, "label": label})
        if completed.returncode != 0:
            log("   compare exited %d for %s" % (completed.returncode, label))
    return produced


def _has_runs(directory: str) -> bool:
    return os.path.isdir(directory) and any(n.endswith(".json") for n in os.listdir(directory))


def git_commit(root: str) -> str:
    try:
        return subprocess.run(["git", "rev-parse", "HEAD"], cwd=root, capture_output=True, text=True,
                              check=True).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return "unknown"


def main(argv: List[str]) -> int:
    args = parse_args(argv)
    manifest = load_manifest(args.manifest) if args.manifest else manifest_from_args(args)
    os.makedirs(manifest["outdir"], exist_ok=True)
    started = time.time()
    bookkeeping = execute(manifest, args.java)
    comparisons = run_comparisons(manifest, args.java, bookkeeping)
    metadata = {
        "schema": MANIFEST_SCHEMA,
        "label": manifest.get("label"),
        "platform": platform.platform(),
        "machine": platform.machine(),
        "cpu_count": os.cpu_count(),
        "samples": manifest["samples"],
        "sides": [s["label"] for s in manifest["sides"]],
        "seed": manifest.get("seed"),
        "duration_seconds": round(time.time() - started, 1),
        "failures": bookkeeping["failures"],
        "comparisons": comparisons,
        "smithy_java_commit": git_commit(REPO_ROOT),
    }
    with open(os.path.join(manifest["outdir"], "run-metadata.json"), "w", encoding="utf-8") as handle:
        json.dump(metadata, handle, indent=2)
    log("runs under %s/raw, comparison files from the manifest's compare list, bookkeeping in run-metadata.json"
        % manifest["outdir"])
    if bookkeeping["failures"]:
        log("%d run failure(s): %s" % (len(bookkeeping["failures"]), ", ".join(bookkeeping["failures"])))
    return 1 if bookkeeping["failures"] else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
