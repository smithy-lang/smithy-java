#!/bin/bash
#
# Host-side half of scripts/run-metal-benchmarks.py. Staged through S3 and executed on the
# bare-metal instance with SSM Run Command; nothing here is run locally.
#
# Subcommands:
#   bootstrap <work_dir> <s3_stage_uri> <java_major>
#       Install a JDK, pin CPU frequency scaling, download the staged jars, print a READINESS line.
#   smoke <work_dir> <instance_type> <e2e_jar>
#       Run one e2e benchmark and print a SMOKE line with the health figures the orchestrator gates on.
#   run <work_dir> <s3_results_uri> <instance_type> <suites_csv> <samples> <current_e2e_jar> \
#       <baseline_e2e_jar|-> <serde_jar|-> <serde_fast 0|1> <e2e_args> <serde_args>
#       Run the benchmark matrix, upload results to S3, write the DONE sentinel. Started detached so
#       it survives the SSM command that launched it; progress goes to progress.log.
#
# Deliberately not `set -e` in `run`: one failing sample must not abandon the rest. Failures are
# counted and reported through the sentinel.

set -uo pipefail

# SSM Run Command shells carry a minimal environment; the detached runner inherits it.
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:${PATH:-}"

log() {
    echo "[$(date -u +%Y-%m-%dT%H:%M:%SZ)] $*"
}

# ---------------------------------------------------------------------------------------------
# bootstrap
# ---------------------------------------------------------------------------------------------
bootstrap() {
    local work_dir=${1:?work_dir required}
    local stage_uri=${2:?s3 stage uri required}
    local java_major=${3:-25}
    mkdir -p "$work_dir/jars" "$work_dir/results" "$work_dir/logs"

    echo "=== installing Amazon Corretto ${java_major}"
    local java_home=""
    if dnf install -y "java-${java_major}-amazon-corretto-devel" >/dev/null 2>&1 \
        || dnf install -y "java-${java_major}-amazon-corretto-headless" >/dev/null 2>&1; then
        java_home=$(dirname "$(dirname "$(readlink -f "$(command -v java)")")")
        echo "    installed from the distribution repository"
    else
        # Not in the distribution repository yet; fetch the generic Linux build from corretto.aws.
        local corretto_arch
        case "$(uname -m)" in
            x86_64) corretto_arch=x64 ;;
            aarch64) corretto_arch=aarch64 ;;
            *) echo "FATAL: unsupported architecture $(uname -m)"; exit 1 ;;
        esac
        local url="https://corretto.aws/downloads/latest/amazon-corretto-${java_major}-${corretto_arch}-linux-jdk.tar.gz"
        echo "    repository package unavailable; downloading $url"
        mkdir -p /opt/corretto
        if ! curl -fsSL "$url" -o /tmp/corretto.tar.gz || ! tar -xzf /tmp/corretto.tar.gz -C /opt/corretto; then
            echo "FATAL: could not download or extract Corretto ${java_major}"
            exit 1
        fi
        java_home=$(find /opt/corretto -maxdepth 1 -mindepth 1 -type d -name 'amazon-corretto-*' | sort | tail -1)
    fi
    if [[ -z "$java_home" || ! -x "$java_home/bin/java" ]]; then
        echo "FATAL: no usable JDK after installation"
        exit 1
    fi
    cat > "$work_dir/java.env" <<ENV
export JAVA_HOME="$java_home"
export PATH="$java_home/bin:\$PATH"
ENV
    # shellcheck disable=SC1090
    source "$work_dir/java.env"
    java -version 2>&1 | sed 's/^/    /'
    local found_major
    found_major=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
    if [[ -z "$found_major" || "$found_major" -lt "$java_major" ]]; then
        echo "FATAL: need Java ${java_major}+, found '${found_major:-none}'"
        exit 1
    fi

    echo
    echo "=== pinning CPU frequency scaling"
    local governor="absent"
    if compgen -G "/sys/devices/system/cpu/cpu*/cpufreq/scaling_governor" >/dev/null; then
        for f in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do
            echo performance > "$f" 2>/dev/null
        done
        governor=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo unknown)
        echo "    scaling_governor=$governor"
    else
        echo "    no cpufreq governor on this platform (expected on Graviton)"
    fi
    local no_turbo="absent"
    if [[ -w /sys/devices/system/cpu/intel_pstate/no_turbo ]]; then
        echo 1 > /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null
        no_turbo=$(cat /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || echo unknown)
        echo "    intel_pstate/no_turbo=$no_turbo"
    else
        echo "    no intel_pstate/no_turbo on this platform"
    fi

    echo
    echo "=== downloading staged jars from $stage_uri/jars/"
    if ! aws s3 cp --recursive --no-progress "$stage_uri/jars/" "$work_dir/jars/" 2>&1 | sed 's/^/    /'; then
        echo "FATAL: could not download jars from $stage_uri/jars/"
        exit 1
    fi
    local jar_count
    jar_count=$(find "$work_dir/jars" -name '*.jar' | wc -l | tr -d ' ')
    find "$work_dir/jars" -name '*.jar' -exec basename {} \; | sed 's/^/    /'
    if [[ "$jar_count" -eq 0 ]]; then
        echo "FATAL: no jars downloaded"
        exit 1
    fi

    echo
    echo "=== host facts"
    local nproc_count model mem
    nproc_count=$(nproc 2>/dev/null || echo unknown)
    model=$(grep -m1 'model name' /proc/cpuinfo 2>/dev/null | cut -d: -f2- | sed 's/^ *//')
    mem=$(free -g 2>/dev/null | awk '/^Mem:/{print $2"GiB"}')
    echo "    nproc=$nproc_count"
    echo "    model=${model:-unknown}"
    echo "    mem=${mem:-unknown}"
    echo "    pending shutdown: $(shutdown --show 2>&1 | head -1)"

    # Parsed by the orchestrator. Keep the format stable.
    echo
    echo "READINESS java_major=$found_major jars=$jar_count governor=$governor no_turbo=$no_turbo nproc=$nproc_count"
}

# ---------------------------------------------------------------------------------------------
# smoke
# ---------------------------------------------------------------------------------------------
smoke() {
    local work_dir=${1:?work_dir required}
    local instance_type=${2:?instance type required}
    local e2e_jar=${3:?e2e jar required}
    # shellcheck disable=SC1090
    source "$work_dir/java.env"
    mkdir -p "$work_dir/logs"
    local out="$work_dir/smoke.json"
    rm -f "$out"
    if ! java -jar "$work_dir/jars/$e2e_jar" --protocol awsJson1_0 --filter GetItemOutput_S \
            --instance-type "$instance_type" --output "$out" > "$work_dir/logs/smoke.log" 2>&1; then
        echo "SMOKE status=failed"
        tail -n 40 "$work_dir/logs/smoke.log"
        exit 1
    fi
    python3 - "$out" <<'PY'
import json, sys
d = json.load(open(sys.argv[1]))
s = d["summary"]
print("SMOKE status=ok ops_per_cpu_sec=%.0f cpu_wall=%.3f under_warmed=%d xbatch=%s" % (
    s["overall"]["ops_per_cpu_sec"],
    s["cpu_wall_ratio"]["mean"],
    s["verification"]["under_warmed_benchmarks"],
    d["metadata"]["background_jit_compilation_disabled"]))
PY
}

# ---------------------------------------------------------------------------------------------
# run
# ---------------------------------------------------------------------------------------------
run() {
    local work_dir=${1:?work_dir required}
    local results_uri=${2:?s3 results uri required}
    local instance_type=${3:?instance type required}
    local suites=${4:?suites csv required}
    local samples=${5:?samples required}
    local current_jar=${6:?current e2e jar required}
    local baseline_jar=${7:--}
    local serde_jar=${8:--}
    local serde_fast=${9:-0}
    local e2e_args_str=${10:-}
    local serde_args_str=${11:-}

    # shellcheck disable=SC1090
    source "$work_dir/java.env"
    local results_dir="$work_dir/results"
    local log_dir="$work_dir/logs"
    local sentinel="$work_dir/DONE"
    local progress="$work_dir/progress.log"
    mkdir -p "$results_dir" "$log_dir"
    rm -f "$sentinel"

    # The passthrough strings were shell-quoted by the orchestrator; eval restores the words.
    local -a e2e_args=() serde_args=()
    eval "e2e_args=($e2e_args_str)"
    eval "serde_args=($serde_args_str)"

    local failures=0 total=0
    local started_at
    started_at=$(date +%s)
    exec >> "$progress" 2>&1
    log "suites=[$suites] samples=$samples instance=$instance_type java=$(java -version 2>&1 | head -1)"
    log "e2e: current=$current_jar baseline=$baseline_jar args=[${e2e_args[*]:-}]"
    log "serde: jar=$serde_jar fast=$serde_fast args=[${serde_args[*]:-}]"

    if [[ ",$suites," == *,e2e,* ]]; then
        local -a sides=()
        [[ "$baseline_jar" != "-" ]] && sides+=(baseline)
        sides+=(current)
        for ((i = 1; i <= samples; i++)); do
            # Interleaved: baseline_1, current_1, baseline_2, ... so time-dependent drift hits both sides alike.
            for side in "${sides[@]}"; do
                total=$((total + 1))
                local jar="$current_jar"
                [[ "$side" == baseline ]] && jar="$baseline_jar"
                local out_dir="$results_dir/e2e/$side"
                mkdir -p "$out_dir"
                local out="$out_dir/run$i.json"
                local run_log="$log_dir/e2e-$side-run$i.log"
                local run_started
                run_started=$(date +%s)
                java -jar "$work_dir/jars/$jar" --instance-type "$instance_type" --output "$out" \
                    --notes "metal run, $side, sample $i of $samples" ${e2e_args[@]+"${e2e_args[@]}"} > "$run_log" 2>&1
                local status=$?
                local elapsed=$(( $(date +%s) - run_started ))
                if [[ $status -ne 0 || ! -f "$out" ]]; then
                    log "FAIL  e2e $side sample $i (exit $status, ${elapsed}s) see $(basename "$run_log")"
                    failures=$((failures + 1))
                else
                    local summary
                    summary=$(python3 - "$out" <<'PY' 2>/dev/null || echo "?"
import json, sys
d = json.load(open(sys.argv[1]))
s = d["summary"]
print("%.0f ops/CPU-sec  cpu/wall %.3f  under-warmed %d/%d" % (
    s["overall"]["ops_per_cpu_sec"], s["cpu_wall_ratio"]["mean"],
    s["verification"]["under_warmed_benchmarks"], d["metadata"]["benchmark_count"]))
PY
)
                    log "ok    e2e $side sample $i (${elapsed}s) $summary"
                fi
            done
        done
    fi

    if [[ ",$suites," == *,serde,* ]]; then
        total=$((total + 1))
        if [[ "$serde_jar" == "-" || ! -f "$work_dir/jars/$serde_jar" ]]; then
            log "FAIL  serde: jar not staged"
            failures=$((failures + 1))
        else
            local out_dir="$results_dir/serde"
            mkdir -p "$out_dir"
            # The JMH jar's runtime dependencies may be nested jars on the manifest Class-Path, so run
            # it from an exploded directory as a real classpath.
            local cp_dir="$work_dir/serde-classpath"
            rm -rf "$cp_dir" && mkdir -p "$cp_dir" && (cd "$cp_dir" && jar xf "$work_dir/jars/$serde_jar")
            local -a jmh=(-bm sample -tu ns -f 1 -rf json -rff "$out_dir/results.json"
                          -jvmArgs "-Xms1g -Xmx1g -XX:+UseG1GC -XX:+AlwaysPreTouch")
            if [[ "$serde_fast" == "1" ]]; then
                jmh+=(-wi 1 -w 5s -i 3 -r 5s)
            else
                jmh+=(-wi 5 -w 2s -i 10 -r 5s)
            fi
            jmh+=(${serde_args[@]+"${serde_args[@]}"})
            # Registered last so its measurement excludes other profilers' setup and teardown.
            jmh+=(-foe true -prof software.amazon.smithy.java.benchmarks.OpsPerCpuSecondProfiler)
            local run_started
            run_started=$(date +%s)
            log "serde: starting JMH ${jmh[*]}"
            java -cp "$cp_dir:$cp_dir/*" org.openjdk.jmh.Main "${jmh[@]}" > "$log_dir/serde-jmh.log" 2>&1
            local status=$?
            if [[ $status -eq 0 ]]; then
                # The converter runs here so EC2 instance type detection (IMDS) works.
                java -cp "$cp_dir:$cp_dir/*" software.amazon.smithy.java.benchmarks.serde.JmhResultConverter \
                    --input "$out_dir/results.json" --output-prefix "$out_dir/output" >> "$log_dir/serde-jmh.log" 2>&1
                status=$?
            fi
            local elapsed=$(( $(date +%s) - run_started ))
            if [[ $status -ne 0 ]]; then
                log "FAIL  serde (exit $status, ${elapsed}s) see serde-jmh.log"
                failures=$((failures + 1))
            else
                log "ok    serde (${elapsed}s) -> serde/output.json, serde/output.md"
            fi
        fi
    fi

    if [[ ",$suites," == *,fixture,* ]]; then
        total=$((total + 1))
        if fixture_experiment "$work_dir" "$instance_type"; then
            log "ok    fixture-server experiment"
        else
            log "FAIL  fixture-server experiment (see logs/fixture-*.log)"
            failures=$((failures + 1))
        fi
    fi

    if [[ ",$suites," == *,h1scaling,* ]]; then
        total=$((total + 1))
        if h1scaling_suite "$work_dir"; then
            log "ok    h1scaling suite"
        else
            log "FAIL  h1scaling suite (see logs/h1scaling-*.log)"
            failures=$((failures + 1))
        fi
    fi

    local total_elapsed=$(( $(date +%s) - started_at ))
    log "matrix complete: $((total - failures))/$total succeeded in ${total_elapsed}s"

    cp "$progress" "$results_dir/host-progress.log" 2>/dev/null
    if aws s3 cp --recursive --no-progress "$results_dir" "$results_uri/results/" >/dev/null 2>&1; then
        log "uploaded results to $results_uri/results/"
    else
        log "ERROR failed to upload results to $results_uri/results/"
        failures=$((failures + 1))
    fi
    local bundle="$work_dir/bundle.tar.gz"
    tar -czf "$bundle" -C "$work_dir" results logs progress.log 2>/dev/null
    if aws s3 cp "$bundle" "$results_uri/bundle.tar.gz" >/dev/null 2>&1; then
        log "uploaded $(du -h "$bundle" | cut -f1) bundle (results + logs) to $results_uri/bundle.tar.gz"
    else
        log "ERROR failed to upload the bundle"
        failures=$((failures + 1))
    fi

    echo "$failures" > "$sentinel"
    log "sentinel written, failures=$failures"
}

# ---------------------------------------------------------------------------------------------
# fixture experiment: run the e2e benchmark over the real transport against the Java fixture server
# (staged as a jar; no build on the host), server and client pinned to disjoint cores, and record
# server CPU per request. Parameters arrive through the environment set by the orchestrator:
#   FIXTURE_BENCHMARKS  comma-separated e2e benchmark ids
#   FIXTURE_RUNS        independent runs per mode
#   FIXTURE_MODES       comma-separated experiment modes (default: stub,https)
#   FIXTURE_SERVER_CPUS / FIXTURE_CLIENT_CPUS  taskset lists (default: 2 and 4-11)
# ---------------------------------------------------------------------------------------------
fixture_experiment() {
    local work_dir=$1 instance_type=$2
    local fixture_dir="$work_dir/fixture"
    local log_dir="$work_dir/logs"
    local out="$work_dir/results/fixture"
    mkdir -p "$out" "$log_dir"
    local benchmarks=${FIXTURE_BENCHMARKS:-rpcv2Cbor_PutItemRequest_Baseline,awsJson1_0_GetItemOutput_M,restXml_PutObject_L,restXml_GetObject_L}
    local runs=${FIXTURE_RUNS:-5}
    local server_cpus=${FIXTURE_SERVER_CPUS:-2}
    local client_cpus=${FIXTURE_CLIENT_CPUS:-4-11}
    local modes=${FIXTURE_MODES:-stub,https}

    # shellcheck disable=SC1090
    source "$work_dir/java.env"
    dnf install -y openssl python3 >> "$log_dir/fixture-setup.log" 2>&1 || true
    # The driver and make-cert.sh were staged next to the jars.
    mkdir -p "$fixture_dir"
    cp "$work_dir/jars/run-transport.py" "$fixture_dir/run-transport.py" 2>/dev/null || true
    cp "$work_dir/jars/make-cert.sh" "$fixture_dir/make-cert.sh" 2>/dev/null || true

    local status=0
    # Run the experiment for the current client and, when a baseline jar is staged, the old client too, so the
    # 4 cases are measured across stub + https for both SDKs. The fixture server (BoringSSL) is shared; the cert
    # is generated once by the first invocation and reused.
    for side in current baseline; do
        local jar="$work_dir/jars/$side.jar"
        if [[ ! -f "$jar" ]]; then
            [[ "$side" == baseline ]] && continue
            log "FAIL  fixture: $side jar missing"; status=1; continue
        fi
        log "fixture: $side experiment, runs=$runs modes=[$modes] benchmarks=[$benchmarks]"
        python3 "$fixture_dir/run-transport.py" --imds --runs "$runs" --label "$instance_type-$side" \
            --benchmarks "$benchmarks" --modes "$modes" --server-taskset "$server_cpus" --client-taskset "$client_cpus" \
            --jar "$jar" --server-jar "$work_dir/jars/fixture-server.jar" \
            --make-cert "$fixture_dir/make-cert.sh" --cert "$fixture_dir/certs/server.pem" --key "$fixture_dir/certs/server-key.pem" \
            --outdir "$out/$side" > "$log_dir/fixture-$side.log" 2>&1 \
            || { log "FAIL  fixture $side"; status=1; }
        tail -n 3 "$log_dir/fixture-$side.log" | sed 's/^/        /'
    done

    # Host facts the plan asks to record alongside results.
    { lscpu; echo; grep -m1 "model name" /proc/cpuinfo; echo; uname -a; head -2 /etc/os-release; } > "$out/host-facts.txt" 2>&1
    return $status
}
# ---------------------------------------------------------------------------------------------
# h1scaling: the http-client module's H1ScalingBenchmark (JMH throughput at 1/10/100 concurrent callers)
# against the module's Netty BenchmarkServer, for the current JMH jar and, if staged, a baseline jar, with
# the workers on platform and/or virtual threads. This is the concurrent-caller check for client transport
# changes; the e2e/fixture suites are sequential by spec. Parameters arrive through the environment:
#   H1_CONCURRENCY      JMH -p concurrency list (default 1,10,100)
#   H1_MAX_CONNECTIONS  JMH -p maxConnections list (default 100)
#   H1_THREADS          comma-separated worker thread kinds: platform, virtual (default both)
#   H1_INCLUDES         JMH benchmark regex (default H1ScalingBenchmark.h1Smithy)
#   H1_SERVER_CPUS / H1_CLIENT_CPUS  taskset lists (default 12-19 and 24-47)
#   H1_FAST             1 for short iterations
# ---------------------------------------------------------------------------------------------
h1scaling_suite() {
    local work_dir=$1
    local log_dir="$work_dir/logs"
    local out="$work_dir/results/h1scaling"
    mkdir -p "$out" "$log_dir"
    local jmh_jar="$work_dir/jars/h1-jmh.jar"
    local server_jar="$work_dir/jars/h1-server.jar"
    local baseline_jar="$work_dir/jars/h1-jmh-baseline.jar"
    local concurrency=${H1_CONCURRENCY:-1,10,100}
    local max_conns=${H1_MAX_CONNECTIONS:-100}
    local threads=${H1_THREADS:-platform,virtual}
    local includes=${H1_INCLUDES:-H1ScalingBenchmark.h1Smithy}
    local server_cpus=${H1_SERVER_CPUS:-12-19}
    local client_cpus=${H1_CLIENT_CPUS:-24-47}
    local fast=${H1_FAST:-0}
    if [[ ! -f "$jmh_jar" || ! -f "$server_jar" ]]; then
        log "FAIL  h1scaling: h1-jmh.jar / h1-server.jar not staged"
        return 1
    fi
    dnf install -y util-linux >> "$log_dir/h1scaling-setup.log" 2>&1 || true

    log "h1scaling: starting BenchmarkServer on cpus $server_cpus"
    taskset -c "$server_cpus" java -cp "$jmh_jar:$server_jar" software.amazon.smithy.java.http.client.BenchmarkServer \
        "$work_dir/h1-ports.properties" > "$log_dir/h1-server.log" 2>&1 &
    local server_pid=$!
    local i ready=0
    for i in $(seq 1 100); do
        if (exec 3<>/dev/tcp/127.0.0.1/18080) 2>/dev/null; then
            ready=1
            break
        fi
        if ! kill -0 "$server_pid" 2>/dev/null; then
            log "FAIL  h1scaling: BenchmarkServer exited during start-up; see logs/h1-server.log"
            return 1
        fi
        sleep 0.2
    done
    if [[ $ready -ne 1 ]]; then
        log "FAIL  h1scaling: BenchmarkServer not listening on 18080"
        kill "$server_pid" 2>/dev/null
        return 1
    fi

    local -a iters=(-wi 2 -w 3s -i 3 -r 5s)
    [[ "$fast" == "1" ]] && iters=(-wi 1 -w 2s -i 2 -r 3s)
    local status=0 side jar mode label run_started
    for side in current baseline; do
        jar=$jmh_jar
        if [[ "$side" == baseline ]]; then
            [[ -f "$baseline_jar" ]] || continue
            jar=$baseline_jar
        fi
        for mode in ${threads//,/ }; do
            label="$side-$mode"
            log "h1scaling: $label includes=$includes concurrency=$concurrency maxConnections=$max_conns cpus=$client_cpus"
            run_started=$(date +%s)
            # The ops/CPU-sec profiler (benchmark-commons) adds a secondary ":ops_per_cpu_sec" result per row, so
            # throughput and CPU cost per request are reported side by side for platform and virtual workers.
            if taskset -c "$client_cpus" java -jar "$jar" "$includes" -p "concurrency=$concurrency" -p "maxConnections=$max_conns" \
                -f 1 "${iters[@]}" -foe true -rf json -rff "$out/$label.json" \
                -prof software.amazon.smithy.java.benchmarks.OpsPerCpuSecondProfiler \
                -jvmArgsAppend "-Djmh.bench.threads=$mode" -jvmArgsAppend "-Djmh.bench.host=127.0.0.1" \
                > "$log_dir/h1scaling-$label.log" 2>&1; then
                log "ok    h1scaling $label ($(( $(date +%s) - run_started ))s)"
                grep -E "^H1ScalingBenchmark\.|^Benchmark " "$log_dir/h1scaling-$label.log" | tail -n 8 | sed 's/^/        /'
            else
                log "FAIL  h1scaling $label; see logs/h1scaling-$label.log"
                tail -n 5 "$log_dir/h1scaling-$label.log" | sed 's/^/        /'
                status=1
            fi
        done
    done
    kill "$server_pid" 2>/dev/null
    wait "$server_pid" 2>/dev/null
    return $status
}

case "${1:-}" in
    bootstrap) shift; bootstrap "$@" ;;
    smoke) shift; smoke "$@" ;;
    run) shift; run "$@" ;;
    *)
        sed -n '3,/^$/s/^# \{0,1\}//p' "$0"
        exit 2
        ;;
esac
