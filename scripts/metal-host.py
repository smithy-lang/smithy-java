#!/usr/bin/env python3
"""Host-side runner for scripts/run-metal-benchmarks.py.

Staged to the bare-metal instance and executed there by SSM Run Command; nothing here runs locally. python3 is
preinstalled on Amazon Linux 2023, so this is the only host-side program: the orchestrator's SSM commands are
one-liners that `aws s3 cp` this file down and `python3 metal-host.py <subcommand> ...`. There is no shell
runner and no `eval`; the benchmark matrix is described by a staged manifest.json and suite-config.json.

Subcommands:
  bootstrap <work_dir> <stage_uri> <java_major>
      Install a JDK, pin CPU frequency scaling, download the staged artifacts, print a READINESS line.
  smoke <work_dir> <instance_type> <e2e_jar>
      Run one e2e benchmark and print a SMOKE line with the health figures the orchestrator gates on.
  run <work_dir> <s3_results_uri> <suites_csv>
      Run the benchmark matrix (e2e/fixture via e2e-scheduler.py, serde and h1scaling via JMH), upload results
      to S3, write the DONE sentinel. Launched detached so it survives the SSM command that started it;
      progress goes to progress.log. One failing suite does not abandon the rest; failures are counted and
      reported through the sentinel.
"""

import glob
import json
import os
import shutil
import subprocess
import sys
import time


def log(message):
    print("[%s] %s" % (time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), message), flush=True)


def fatal(message):
    print("FATAL: " + message, flush=True)
    sys.exit(1)


def sh(command, **kwargs):
    """Run a command, inheriting stdout/stderr unless redirected by the caller."""
    return subprocess.run(command, **kwargs)


def read_json(path, key_path, default=None):
    try:
        with open(path, encoding="utf-8") as handle:
            node = json.load(handle)
    except (OSError, ValueError):
        return default
    for part in key_path.split("."):
        if isinstance(node, dict) and part in node:
            node = node[part]
        else:
            return default
    return node


def java_home(work_dir):
    with open(os.path.join(work_dir, "java-home.txt"), encoding="utf-8") as handle:
        return handle.read().strip()


# -------------------------------------------------------------------------------------------------
# bootstrap
# -------------------------------------------------------------------------------------------------
def install_jdk(major):
    for package in ("java-%d-amazon-corretto-devel" % major, "java-%d-amazon-corretto-headless" % major):
        if sh(["dnf", "install", "-y", package], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
            java = shutil.which("java")
            if java:
                print("    installed from the distribution repository")
                return os.path.dirname(os.path.dirname(os.path.realpath(java)))
    arch = {"x86_64": "x64", "aarch64": "aarch64"}.get(os.uname().machine)
    if not arch:
        fatal("unsupported architecture %s" % os.uname().machine)
    url = "https://corretto.aws/downloads/latest/amazon-corretto-%d-%s-linux-jdk.tar.gz" % (major, arch)
    print("    repository package unavailable; downloading %s" % url)
    os.makedirs("/opt/corretto", exist_ok=True)
    if sh(["curl", "-fsSL", url, "-o", "/tmp/corretto.tar.gz"]).returncode != 0 \
            or sh(["tar", "-xzf", "/tmp/corretto.tar.gz", "-C", "/opt/corretto"]).returncode != 0:
        fatal("could not download or extract Corretto %d" % major)
    found = sorted(glob.glob("/opt/corretto/amazon-corretto-*"))
    return found[-1] if found else None


def pin_governor():
    paths = glob.glob("/sys/devices/system/cpu/cpu*/cpufreq/scaling_governor")
    if not paths:
        print("    no cpufreq governor on this platform (expected on Graviton)")
        return "absent"
    for path in paths:
        try:
            with open(path, "w", encoding="utf-8") as handle:
                handle.write("performance")
        except OSError:
            pass
    try:
        with open("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor", encoding="utf-8") as handle:
            governor = handle.read().strip()
    except OSError:
        governor = "unknown"
    print("    scaling_governor=%s" % governor)
    return governor


def pin_no_turbo():
    path = "/sys/devices/system/cpu/intel_pstate/no_turbo"
    if not os.path.exists(path):
        print("    no intel_pstate/no_turbo on this platform")
        return "absent"
    try:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write("1")
    except OSError:
        pass
    try:
        with open(path, encoding="utf-8") as handle:
            value = handle.read().strip()
    except OSError:
        value = "unknown"
    print("    intel_pstate/no_turbo=%s" % value)
    return value


def bootstrap(work_dir, stage_uri, java_major):
    java_major = int(java_major)
    for directory in ("jars", "results", "logs"):
        os.makedirs(os.path.join(work_dir, directory), exist_ok=True)

    print("=== installing Amazon Corretto %d" % java_major)
    home = install_jdk(java_major)
    if not home or not os.path.isfile(os.path.join(home, "bin", "java")):
        fatal("no usable JDK after installation")
    with open(os.path.join(work_dir, "java-home.txt"), "w", encoding="utf-8") as handle:
        handle.write(home)
    java = os.path.join(home, "bin", "java")
    version_text = sh([java, "-version"], capture_output=True, text=True).stderr
    for line in version_text.splitlines():
        print("    " + line)
    digits = "".join(c for c in version_text.split('"')[1] if c.isdigit()) if '"' in version_text else ""
    found_major = int(digits[:2]) if digits.startswith(("1", "2")) else (int(digits) if digits.isdigit() else 0)
    # The version string is like "21.0.5"; take the leading major component robustly.
    try:
        found_major = int(version_text.split('"')[1].split(".")[0])
    except (IndexError, ValueError):
        found_major = 0
    if found_major < java_major:
        fatal("need Java %d+, found '%s'" % (java_major, found_major or "none"))

    print()
    print("=== pinning CPU frequency scaling")
    governor = pin_governor()
    no_turbo = pin_no_turbo()

    print()
    print("=== downloading staged artifacts from %s/jars/" % stage_uri)
    if sh(["aws", "s3", "cp", "--recursive", "--no-progress", "%s/jars/" % stage_uri,
           os.path.join(work_dir, "jars") + "/"]).returncode != 0:
        fatal("could not download artifacts from %s/jars/" % stage_uri)
    jar_names = [n for n in os.listdir(os.path.join(work_dir, "jars"))]
    jar_count = len([n for n in jar_names if n.endswith(".jar")])
    for name in sorted(jar_names):
        print("    " + name)
    if jar_count == 0:
        fatal("no jars downloaded")

    print()
    print("=== host facts")
    nproc = os.cpu_count() or 0
    print("    nproc=%s" % nproc)
    print()
    print("READINESS java_major=%s jars=%d governor=%s no_turbo=%s nproc=%s"
          % (found_major, jar_count, governor, no_turbo, nproc))


# -------------------------------------------------------------------------------------------------
# smoke
# -------------------------------------------------------------------------------------------------
def smoke(work_dir, instance_type, e2e_jar):
    java = os.path.join(java_home(work_dir), "bin", "java")
    os.makedirs(os.path.join(work_dir, "logs"), exist_ok=True)
    out = os.path.join(work_dir, "smoke.json")
    if os.path.exists(out):
        os.remove(out)
    smoke_log = os.path.join(work_dir, "logs", "smoke.log")
    with open(smoke_log, "w", encoding="utf-8") as handle:
        status = sh([java, "-jar", os.path.join(work_dir, "jars", e2e_jar), "--protocol", "awsJson1_0",
                     "--filter", "GetItemOutput_S", "--instance-type", instance_type, "--output", out],
                    stdout=handle, stderr=subprocess.STDOUT).returncode
    if status != 0 or not os.path.isfile(out):
        print("SMOKE status=failed")
        with open(smoke_log, encoding="utf-8") as handle:
            print("".join(handle.readlines()[-40:]))
        sys.exit(1)
    with open(out, encoding="utf-8") as handle:
        report = json.load(handle)
    summary = report["summary"]
    print("SMOKE status=ok ops_per_cpu_sec=%.0f cpu_wall=%.3f under_warmed=%d xbatch=%s" % (
        summary["overall"]["ops_per_cpu_sec"], summary["cpu_wall_ratio"]["mean"],
        summary["verification"]["under_warmed_benchmarks"],
        report["metadata"]["background_jit_compilation_disabled"]))


# -------------------------------------------------------------------------------------------------
# run: the matrix
# -------------------------------------------------------------------------------------------------
def e2e_fixture_suite(work_dir, java, log_dir):
    """e2e + fixture are one manifest-driven scheduler run that also invokes the one comparison tool."""
    manifest = os.path.join(work_dir, "jars", "manifest.json")
    if not os.path.isfile(manifest):
        log("FAIL  e2e/fixture: run manifest not staged at %s" % manifest)
        return False
    scheduler = os.path.join(work_dir, "jars", "e2e-scheduler.py")
    scheduler_log = os.path.join(log_dir, "e2e-scheduler.log")
    with open(scheduler_log, "w", encoding="utf-8") as handle:
        status = sh(["python3", scheduler, "--manifest", manifest, "--java", java],
                    stdout=handle, stderr=subprocess.STDOUT).returncode
    tail = tail_lines(scheduler_log, 8 if status == 0 else 20)
    for line in tail:
        log("        " + line)
    if status != 0:
        log("FAIL  e2e/fixture scheduler (see logs/e2e-scheduler.log)")
        return False
    log("ok    e2e/fixture scheduler")
    return True


def serde_suite(work_dir, java, log_dir, suite_config):
    serde_jar = read_json(suite_config, "serde.jar", "-")
    serde_fast = read_json(suite_config, "serde.fast", False)
    serde_args = read_json(suite_config, "serde.args", []) or []
    staged = os.path.join(work_dir, "jars", serde_jar) if serde_jar and serde_jar != "-" else None
    if not staged or not os.path.isfile(staged):
        log("FAIL  serde: jar not staged (%s)" % serde_jar)
        return False
    out_dir = os.path.join(work_dir, "results", "serde")
    os.makedirs(out_dir, exist_ok=True)
    # The JMH jar's runtime dependencies may be nested jars on the manifest Class-Path, so run it from an
    # exploded directory as a real classpath.
    cp_dir = os.path.join(work_dir, "serde-classpath")
    shutil.rmtree(cp_dir, ignore_errors=True)
    os.makedirs(cp_dir, exist_ok=True)
    sh(["jar", "xf", staged], cwd=cp_dir)
    classpath = "%s:%s/*" % (cp_dir, cp_dir)
    jmh = [java, "-cp", classpath, "org.openjdk.jmh.Main", "-bm", "sample", "-tu", "ns", "-f", "1",
           "-rf", "json", "-rff", os.path.join(out_dir, "results.json"),
           "-jvmArgs", "-Xms1g -Xmx1g -XX:+UseG1GC -XX:+AlwaysPreTouch"]
    jmh += (["-wi", "1", "-w", "5s", "-i", "3", "-r", "5s"] if serde_fast
            else ["-wi", "5", "-w", "2s", "-i", "10", "-r", "5s"])
    jmh += [str(a) for a in serde_args]
    # Registered last so its measurement excludes other profilers' setup and teardown.
    jmh += ["-foe", "true", "-prof", "software.amazon.smithy.java.benchmarks.OpsPerCpuSecondProfiler"]
    serde_log = os.path.join(log_dir, "serde-jmh.log")
    log("serde: starting JMH")
    with open(serde_log, "w", encoding="utf-8") as handle:
        status = sh(jmh, stdout=handle, stderr=subprocess.STDOUT).returncode
        if status == 0:
            # The converter runs here so EC2 instance type detection (IMDS) works.
            status = sh([java, "-cp", classpath, "software.amazon.smithy.java.benchmarks.serde.JmhResultConverter",
                         "--input", os.path.join(out_dir, "results.json"),
                         "--output-prefix", os.path.join(out_dir, "output")],
                        stdout=handle, stderr=subprocess.STDOUT).returncode
    if status != 0:
        log("FAIL  serde (exit %d) see serde-jmh.log" % status)
        return False
    log("ok    serde -> serde/output.json, serde/output.md")
    return True


def h1scaling_suite(work_dir, java, log_dir, suite_config):
    out = os.path.join(work_dir, "results", "h1scaling")
    os.makedirs(out, exist_ok=True)
    jmh_jar = os.path.join(work_dir, "jars", "h1-jmh.jar")
    server_jar = os.path.join(work_dir, "jars", "h1-server.jar")
    baseline_jar = os.path.join(work_dir, "jars", "h1-jmh-baseline.jar")
    if not (os.path.isfile(jmh_jar) and os.path.isfile(server_jar)):
        log("FAIL  h1scaling: h1-jmh.jar / h1-server.jar not staged")
        return False
    concurrency = str(read_json(suite_config, "h1.concurrency", "1,10,100"))
    max_conns = str(read_json(suite_config, "h1.max_connections", "100"))
    threads = str(read_json(suite_config, "h1.threads", "platform,virtual"))
    includes = str(read_json(suite_config, "h1.includes", "H1ScalingBenchmark.h1Smithy"))
    server_cpus = str(read_json(suite_config, "h1.server_cpus", "12-19"))
    client_cpus = str(read_json(suite_config, "h1.client_cpus", "24-47"))
    fast = bool(read_json(suite_config, "h1.fast", False))

    log("h1scaling: starting BenchmarkServer on cpus %s" % server_cpus)
    server_log = os.path.join(log_dir, "h1-server.log")
    server_handle = open(server_log, "w", encoding="utf-8")
    server = subprocess.Popen(
        ["taskset", "-c", server_cpus, java, "-cp", "%s:%s" % (jmh_jar, server_jar),
         "software.amazon.smithy.java.http.client.BenchmarkServer", os.path.join(work_dir, "h1-ports.properties")],
        stdout=server_handle, stderr=subprocess.STDOUT)
    try:
        if not wait_for_port(18080, server):
            log("FAIL  h1scaling: BenchmarkServer not listening on 18080; see logs/h1-server.log")
            return False
        iters = (["-wi", "1", "-w", "2s", "-i", "2", "-r", "3s"] if fast
                 else ["-wi", "2", "-w", "3s", "-i", "3", "-r", "5s"])
        status_ok = True
        for side in ("current", "baseline"):
            jar = jmh_jar
            if side == "baseline":
                if not os.path.isfile(baseline_jar):
                    continue
                jar = baseline_jar
            default_only_done = False
            for mode in [m.strip() for m in threads.split(",") if m.strip()]:
                if side == "baseline" and default_only_done:
                    log("skip  h1scaling baseline-%s: this jar ignores -Djmh.bench.threads; its built-in default "
                        "was already measured as baseline-default-threads" % mode)
                    continue
                label = "%s-%s" % (side, mode)
                result = os.path.join(out, "%s.json" % label)
                run_log = os.path.join(log_dir, "h1scaling-%s.log" % label)
                log("h1scaling: %s includes=%s concurrency=%s maxConnections=%s cpus=%s"
                    % (label, includes, concurrency, max_conns, client_cpus))
                command = ["taskset", "-c", client_cpus, java, "-jar", jar, includes,
                           "-p", "concurrency=%s" % concurrency, "-p", "maxConnections=%s" % max_conns,
                           "-f", "1"] + iters + ["-foe", "true", "-rf", "json", "-rff", result,
                           "-prof", "software.amazon.smithy.java.benchmarks.OpsPerCpuSecondProfiler",
                           "-jvmArgsAppend", "-Djmh.bench.threads=%s" % mode,
                           "-jvmArgsAppend", "-Djmh.bench.host=127.0.0.1"]
                with open(run_log, "w", encoding="utf-8") as handle:
                    status = sh(command, stdout=handle, stderr=subprocess.STDOUT).returncode
                if status != 0:
                    log("FAIL  h1scaling %s; see logs/h1scaling-%s.log" % (label, label))
                    status_ok = False
                    continue
                log("ok    h1scaling %s" % label)
                # Verify the jar honoured the requested worker mode. The current harness prints a
                # BENCH_THREAD_MODE line from BenchmarkSupport's static init; a pre-flag baseline jar prints
                # none, so its run is the per-invocation virtual default regardless of -Djmh.bench.threads.
                detected = detect_thread_mode(run_log)
                if side == "baseline" and detected is None:
                    log("WARN  h1scaling baseline-%s reported no thread mode: the jar pre-dates "
                        "-Djmh.bench.threads and ran its built-in virtual default, NOT '%s'. Recording once as "
                        "baseline-default-threads." % (mode, mode))
                    rename(result, os.path.join(out, "baseline-default-threads.json"))
                    default_only_done = True
                elif detected is not None and detected != mode:
                    log("WARN  h1scaling %s requested '%s' but the jar reports effective '%s'; recording as %s-%s."
                        % (label, mode, detected, side, detected))
                    rename(result, os.path.join(out, "%s-%s.json" % (side, detected)))
        return status_ok
    finally:
        server.terminate()
        try:
            server.wait(timeout=10)
        except subprocess.TimeoutExpired:
            server.kill()
        server_handle.close()


def do_run(work_dir, results_uri, suites):
    results_dir = os.path.join(work_dir, "results")
    log_dir = os.path.join(work_dir, "logs")
    sentinel = os.path.join(work_dir, "DONE")
    progress = os.path.join(work_dir, "progress.log")
    suite_config = os.path.join(work_dir, "jars", "suite-config.json")
    os.makedirs(results_dir, exist_ok=True)
    os.makedirs(log_dir, exist_ok=True)
    if os.path.exists(sentinel):
        os.remove(sentinel)
    java = os.path.join(java_home(work_dir), "bin", "java")

    # Everything the detached run prints goes to progress.log, as the shell runner's `exec >> progress` did.
    progress_handle = open(progress, "a", buffering=1, encoding="utf-8")
    os.dup2(progress_handle.fileno(), 1)
    os.dup2(progress_handle.fileno(), 2)

    suite_set = set(s.strip() for s in suites.split(",") if s.strip())
    failures = 0
    total = 0
    started = time.time()
    log("suites=[%s] java=%s" % (",".join(sorted(suite_set)), java))

    if suite_set & {"e2e", "fixture"}:
        total += 1
        if not e2e_fixture_suite(work_dir, java, log_dir):
            failures += 1
    if "serde" in suite_set:
        total += 1
        if not serde_suite(work_dir, java, log_dir, suite_config):
            failures += 1
    if "h1scaling" in suite_set:
        total += 1
        if not h1scaling_suite(work_dir, java, log_dir, suite_config):
            failures += 1

    log("matrix complete: %d/%d suite(s) succeeded in %ds" % (total - failures, total, int(time.time() - started)))

    shutil.copyfile(progress, os.path.join(results_dir, "host-progress.log"))
    if sh(["aws", "s3", "cp", "--recursive", "--no-progress", results_dir, "%s/results/" % results_uri],
          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        log("uploaded results to %s/results/" % results_uri)
    else:
        log("ERROR failed to upload results to %s/results/" % results_uri)
        failures += 1
    bundle = os.path.join(work_dir, "bundle.tar.gz")
    sh(["tar", "-czf", bundle, "-C", work_dir, "results", "logs", "progress.log"],
       stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    if sh(["aws", "s3", "cp", "--no-progress", bundle, "%s/bundle.tar.gz" % results_uri],
          stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
        log("uploaded bundle (results + logs) to %s/bundle.tar.gz" % results_uri)
    else:
        log("ERROR failed to upload the bundle")
        failures += 1

    with open(sentinel, "w", encoding="utf-8") as handle:
        handle.write(str(failures))
    log("sentinel written, failures=%d" % failures)


# -------------------------------------------------------------------------------------------------
# small helpers
# -------------------------------------------------------------------------------------------------
def tail_lines(path, count):
    try:
        with open(path, encoding="utf-8") as handle:
            return [line.rstrip("\n") for line in handle.readlines()[-count:]]
    except OSError:
        return []


def detect_thread_mode(run_log):
    for line in tail_lines(run_log, 10_000):
        if line.startswith("BENCH_THREAD_MODE="):
            value = line[len("BENCH_THREAD_MODE="):].split()[0].split("=")[0]
            return value.strip()
    return None


def rename(src, dst):
    try:
        os.replace(src, dst)
    except OSError:
        pass


def wait_for_port(port, process, attempts=100):
    import socket
    for _ in range(attempts):
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=1):
                return True
        except OSError:
            pass
        if process.poll() is not None:
            return False
        time.sleep(0.2)
    return False


def main(argv):
    if not argv:
        sys.exit(__doc__)
    command, rest = argv[0], argv[1:]
    if command == "bootstrap":
        bootstrap(*rest)
    elif command == "smoke":
        smoke(*rest)
    elif command == "run":
        do_run(*rest)
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main(sys.argv[1:])
