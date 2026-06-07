#!/usr/bin/env bash
#
# Run serde benchmarks on a remote host via SSH.
#
# Builds the JMH fat jar locally, copies it to the remote host, runs benchmarks,
# then copies the results back. The converter runs on the remote host so that
# EC2 instance type detection (IMDS) works.
#
# Usage:
#   scripts/run-remote-benchmarks.sh <ssh-host> [options]
#
#   <ssh-host>                SSH host alias from ~/.ssh/config
#
# Options:
#   --fast                    Fast mode (1 warmup @ 5s, 3 measurement @ 5s)
#   --includes <regex>        JMH benchmark filter (e.g. RpcV2CborSerialize)
#   --test-case-id <id>       Filter to a single test case ID
#   --profilers <list>        Comma-separated JMH profilers (e.g. gc,stack)
#   --skip-build              Skip local jar build (use existing jar)
#   --remote-java <path>      Path to java on remote host (default: java)
#   --remote-dir <path>       Working directory on remote (default: /tmp/smithy-benchmarks)
#   --output-dir <path>       Local directory for results (default: build/results/jmh)
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR_DIR="$PROJECT_ROOT/benchmarks/serde-benchmarks/build/libs"
LOCAL_RESULTS="$PROJECT_ROOT/benchmarks/serde-benchmarks/build/results/jmh"

# Defaults
FAST=false
INCLUDES=""
TEST_CASE_ID=""
PROFILERS=""
SKIP_BUILD=false
REMOTE_JAVA="java"
REMOTE_DIR="/tmp/smithy-benchmarks"
OUTPUT_DIR=""

usage() {
    sed -n '3,/^$/s/^# //p' "$0"
    exit 1
}

[[ $# -lt 1 || "$1" == "-h" || "$1" == "--help" ]] && usage
SSH_HOST="$1"; shift

while [[ $# -gt 0 ]]; do
    case "$1" in
        --fast)           FAST=true; shift ;;
        --includes)       INCLUDES="$2"; shift 2 ;;
        --test-case-id)   TEST_CASE_ID="$2"; shift 2 ;;
        --profilers)      PROFILERS="$2"; shift 2 ;;
        --skip-build)     SKIP_BUILD=true; shift ;;
        --remote-java)    REMOTE_JAVA="$2"; shift 2 ;;
        --remote-dir)     REMOTE_DIR="$2"; shift 2 ;;
        --output-dir)     OUTPUT_DIR="$2"; shift 2 ;;
        -h|--help)        usage ;;
        *)                echo "Unknown option: $1" >&2; usage ;;
    esac
done

# --- Build locally ---
if [[ "$SKIP_BUILD" == false ]]; then
    echo "==> Building JMH jar..."
    "$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" :benchmarks:serde-benchmarks:jmhJar -q
fi

JAR=$(ls "$JAR_DIR"/*-jmh.jar 2>/dev/null | head -1)
if [[ -z "$JAR" ]]; then
    echo "ERROR: No JMH jar found in $JAR_DIR" >&2
    echo "Run without --skip-build or build with: ./gradlew :benchmarks:serde-benchmarks:jmhJar" >&2
    exit 1
fi
JAR_NAME=$(basename "$JAR")
echo "==> Using jar: $JAR_NAME"

# --- Copy jar to remote ---
echo "==> Copying jar to $SSH_HOST:$REMOTE_DIR/"
ssh "$SSH_HOST" "mkdir -p $REMOTE_DIR"
scp -q "$JAR" "$SSH_HOST:$REMOTE_DIR/$JAR_NAME"

# --- Build JMH CLI args ---
JVM_ARGS="-Xms1g -Xmx1g -XX:+UseG1GC -XX:+AlwaysPreTouch -Dsmithy-java.json-provider=smithy -Dsmithy-java.xml-provider=smithy"

JMH_ARGS="-bm sample -tu ns -f 1 -rf json -rff $REMOTE_DIR/results.json"
JMH_ARGS="$JMH_ARGS -jvmArgs \"$JVM_ARGS\""
if [[ "$FAST" == true ]]; then
    JMH_ARGS="$JMH_ARGS -wi 1 -w 5s -i 3 -r 5s"
else
    JMH_ARGS="$JMH_ARGS -wi 5 -w 2s -i 10 -r 5s"
fi

[[ -n "$INCLUDES" ]] && JMH_ARGS="$JMH_ARGS $INCLUDES"
[[ -n "$TEST_CASE_ID" ]] && JMH_ARGS="$JMH_ARGS -p testCaseId=$TEST_CASE_ID"
[[ -n "$PROFILERS" ]] && JMH_ARGS="$JMH_ARGS -prof $PROFILERS"

# --- Run benchmarks on remote ---
echo "==> Running benchmarks on $SSH_HOST..."
echo "    $REMOTE_JAVA -jar $JAR_NAME $JMH_ARGS"
ssh "$SSH_HOST" "$REMOTE_JAVA -jar $REMOTE_DIR/$JAR_NAME $JMH_ARGS"

# --- Run converter on remote ---
echo "==> Converting results on $SSH_HOST..."
ssh "$SSH_HOST" "$REMOTE_JAVA -cp $REMOTE_DIR/$JAR_NAME \
    software.amazon.smithy.java.benchmarks.serde.JmhResultConverter \
    --input $REMOTE_DIR/results.json \
    --output-prefix $REMOTE_DIR/output"

# --- Copy results back ---
DEST="${OUTPUT_DIR:-$LOCAL_RESULTS}"
echo "==> Copying results to $DEST..."
mkdir -p "$DEST"
scp -q "$SSH_HOST:$REMOTE_DIR/results.json" "$DEST/results.json"
scp -q "$SSH_HOST:$REMOTE_DIR/output.json" "$DEST/output.json"
scp -q "$SSH_HOST:$REMOTE_DIR/output.md" "$DEST/output.md"

echo ""
echo "Results:"
echo "  JSON:     $DEST/output.json"
echo "  Markdown: $DEST/output.md"
echo "  Raw JMH:  $DEST/results.json"