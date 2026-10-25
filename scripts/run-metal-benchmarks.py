#!/usr/bin/env python3
"""Run the smithy-java e2e benchmark jar on a bare-metal EC2 host through SSM and S3.

The host installs Java, pins the CPU governor, downloads the jars, runs interleaved baseline and
current samples with `java -Xbatch -jar`, and uploads the result files.

  run (default)  build or take the jar(s), launch the host, run, download results, terminate
  setup-infra    create the S3 bucket, IAM role and instance profile once per account
  cleanup        terminate the instance recorded in the state file (or --instance-id)

The instance is retained when --keep-instance is given or result files are missing.
The host shuts itself down after --max-hours."""

import argparse
import json
import math
import os
import re
import shlex
import signal
import subprocess
import sys
import time
from typing import Dict, List, Optional, Tuple

try:
    import boto3
    from botocore.exceptions import ClientError
except ImportError:  # pragma: no cover
    sys.exit("boto3 is required: pip install boto3")

PROJECT_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
E2E_JAR = os.path.join(PROJECT_ROOT, "benchmarks", "e2e-benchmarks", "build", "libs", "smithy-java-e2e-benchmark.jar")
GRADLE_TASK = ":benchmarks:e2e-benchmarks:shadowJar"

DEFAULT_REGION = "us-east-1"
DEFAULT_INSTANCE_TYPE = "m7i.metal-24xl"
DEFAULT_INSTANCE_PROFILE = "SmithyJavaBenchmarkHost"
DEFAULT_PREFIX = "ops-cpusec/smithy-java"
DEFAULT_OUTDIR = os.path.join(PROJECT_ROOT, "build", "metal-runs")
HOST_WORK_DIR = "/opt/smithy-java-benchmark"
STATE_FILE = "metal-run-state.json"
TAG_MANAGED_BY = "smithy-java-run-metal-benchmarks"
SSM_MANAGED_POLICY = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
AMI_PARAMETERS = {
    "x86_64": "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64",
    "arm64": "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64",
}
HOURLY_COST = {"m7i.metal-24xl": 4.84, "m7g.metal": 2.61}
SSM_ONLINE_TIMEOUT_SECONDS = 25 * 60

HOST_SCRIPT = r"""#!/bin/bash
set -u
WORK=__WORK__
RESULTS=$WORK/results
mkdir -p $WORK/jars $WORK/logs $RESULTS
exec >> $WORK/progress.log 2>&1
log() { echo "[$(date -u +%H:%M:%S)] $*"; }

log "installing Amazon Corretto __JAVA_MAJOR__"
if ! dnf install -y java-__JAVA_MAJOR__-amazon-corretto-headless >/dev/null 2>&1 \
   && ! dnf install -y java-__JAVA_MAJOR__-amazon-corretto-devel >/dev/null 2>&1; then
    ARCH=$(uname -m | sed 's/x86_64/x64/')
    curl -fsSL "https://corretto.aws/downloads/latest/amazon-corretto-__JAVA_MAJOR__-$ARCH-linux-jdk.tar.gz" -o /tmp/corretto.tgz \
        && mkdir -p /opt/corretto && tar -xzf /tmp/corretto.tgz -C /opt/corretto \
        && export PATH=$(ls -d /opt/corretto/amazon-corretto-* | tail -1)/bin:$PATH
fi
if ! java -version >/dev/null 2>&1; then log "FATAL no java"; echo 1 > $WORK/DONE; exit 1; fi
java -version 2>&1 | sed 's/^/    /'

log "pinning the CPU governor"
for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do echo performance > "$g" 2>/dev/null; done
[ -e /sys/devices/system/cpu/intel_pstate/no_turbo ] && echo 1 > /sys/devices/system/cpu/intel_pstate/no_turbo
log "governor=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null || echo absent) no_turbo=$(cat /sys/devices/system/cpu/intel_pstate/no_turbo 2>/dev/null || echo absent) nproc=$(nproc)"

log "downloading jars from __STAGE__/jars/"
aws s3 cp --recursive --no-progress __STAGE__/jars/ $WORK/jars/ | sed 's/^/    /'

failures=0
for sample in $(seq 1 __SAMPLES__); do
    for side in __SIDES__; do
        out=$RESULTS/$side/sample$sample.json
        mkdir -p "$(dirname "$out")"
        log "$side sample $sample"
        if java -Xbatch -jar $WORK/jars/$side.jar __E2E_ARGS__ --instance-type __INSTANCE_TYPE__ \
               --notes "metal run __RUN_ID__, $side sample $sample" --output "$out" > $WORK/logs/$side-sample$sample.log 2>&1; then
            grep -E "Overall|CPU/wall|Under-warmed" $WORK/logs/$side-sample$sample.log | sed 's/^/    /'
        else
            log "FAIL $side sample $sample (see logs/$side-sample$sample.log)"
            tail -n 20 $WORK/logs/$side-sample$sample.log | sed 's/^/    /'
            failures=$((failures + 1))
        fi
    done
done

expected=$(find $RESULTS -type f | wc -l | tr -d ' ')
log "uploading $expected result file(s) to __RESULTS_URI__/results/"
if ! aws s3 cp --recursive --no-progress $RESULTS __RESULTS_URI__/results/ > $WORK/logs/upload.log 2>&1; then
    log "FAIL uploading results"
    tail -n 20 $WORK/logs/upload.log | sed 's/^/    /'
    failures=$((failures + 1))
fi
uploaded=$(aws s3 ls --recursive __RESULTS_URI__/results/ 2>/dev/null | wc -l | tr -d ' ')
if [ "$uploaded" != "$expected" ]; then
    log "FAIL only $uploaded of $expected result file(s) are in S3; they remain under $RESULTS on this host"
    failures=$((failures + 1))
fi
aws s3 cp --recursive --no-progress $WORK/logs __RESULTS_URI__/logs/ > /dev/null 2>&1 || log "WARN could not upload logs"
aws s3 cp --no-progress $WORK/progress.log __RESULTS_URI__/progress.log > /dev/null 2>&1 || true
log "done, failures=$failures"
echo $failures > $WORK/DONE
"""


def log(message: str) -> None:
    print("[%s] %s" % (time.strftime("%H:%M:%S"), message), flush=True)


def fail(message: str) -> None:
    raise SystemExit("error: " + message)


def compact(instance_type: str) -> str:
    return re.sub(r"[^a-z0-9]", "", instance_type.lower())


def parse_args(argv: List[str]) -> argparse.Namespace:
    command = "run"
    if argv and argv[0] in ("run", "setup-infra", "cleanup"):
        command = argv.pop(0)

    parser = argparse.ArgumentParser(
        prog="run-metal-benchmarks.py [run|setup-infra|cleanup]",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    aws = parser.add_argument_group("AWS")
    aws.add_argument("--region", default=DEFAULT_REGION, help="region to launch in (default %(default)s)")
    aws.add_argument("--profile", default=None, help="AWS credentials profile (default: the environment's)")
    aws.add_argument("--bucket", default=None, help="S3 bucket for staging and results (default perf-comparison-temp-<account-id>)")
    aws.add_argument("--prefix", default=DEFAULT_PREFIX, help="S3 key prefix; <arch>/<run-id>/ is appended (default %(default)s)")
    aws.add_argument("--instance-profile", default=DEFAULT_INSTANCE_PROFILE,
                     help="IAM instance profile with SSM core + bucket access (default %(default)s)")

    host = parser.add_argument_group("host")
    host.add_argument("--instance-type", default=DEFAULT_INSTANCE_TYPE, help="m7i.metal-24xl (x86) or m7g.metal (Graviton); default %(default)s")
    host.add_argument("--subnet-id", default=None, help="launch into this subnet instead of the default VPC")
    host.add_argument("--no-public-ip", action="store_true",
                      help="do not associate a public IP; the subnet then needs NAT or SSM/S3 VPC endpoints")
    host.add_argument("--java-major", type=int, default=25, help="Corretto major version to install (default %(default)s)")
    host.add_argument("--max-hours", type=float, default=6.0,
                      help="dead-man switch: the host shuts itself down after this many hours (default %(default)s)")

    bench = parser.add_argument_group("benchmark")
    bench.add_argument("--current-jar", default=None, help="e2e jar to measure (default: build it with Gradle)")
    bench.add_argument("--baseline-jar", default=None, help="e2e jar built from the baseline commit; measured interleaved with current")
    bench.add_argument("--samples", type=int, default=3, help="JVM invocations per side, interleaved (default %(default)s)")
    bench.add_argument("--e2e-args", default="", help="extra arguments for every run, e.g. \"--mode https\" or \"--protocol rpcv2Cbor\"")
    bench.add_argument("--skip-build", action="store_true", help="use the jar already in build/libs")

    flow = parser.add_argument_group("flow")
    flow.add_argument("--outdir", default=DEFAULT_OUTDIR, help="local directory for results and state (default %(default)s)")
    flow.add_argument("--run-id", default=None, help="override the generated run id")
    flow.add_argument("--poll-seconds", type=int, default=60, help="how often to poll the host (default %(default)s)")
    flow.add_argument("--keep-instance", action="store_true", help="do not terminate the instance at the end")
    flow.add_argument("--instance-id", default=None, help="cleanup: instance to terminate instead of the state file's")
    flow.add_argument("--dry-run", action="store_true", help="validate and print the plan without launching anything")

    args = parser.parse_args(argv)
    args.command = command
    if args.samples < 1:
        parser.error("--samples must be at least 1")
    if not math.isfinite(args.max_hours) or args.max_hours <= 0:
        parser.error("--max-hours must be finite and positive")
    if args.poll_seconds < 1:
        parser.error("--poll-seconds must be at least 1")
    args.e2e_arg_list = shlex.split(args.e2e_args) if args.e2e_args else []
    for flag in ("--output", "--instance-type", "--notes"):
        if flag in args.e2e_arg_list:
            parser.error("%s is set by the host script; do not pass it in --e2e-args" % flag)
    return args


def host_script(args: argparse.Namespace, run_id: str, stage_uri: str, results_uri: str, sides: List[str]) -> str:
    replacements = {
        "__WORK__": HOST_WORK_DIR,
        "__JAVA_MAJOR__": str(args.java_major),
        "__STAGE__": stage_uri,
        "__RESULTS_URI__": results_uri,
        "__SAMPLES__": str(args.samples),
        "__SIDES__": " ".join(sides),
        "__E2E_ARGS__": shlex.join(args.e2e_arg_list),
        "__INSTANCE_TYPE__": shlex.quote(args.instance_type),
        "__RUN_ID__": run_id,
    }
    script = HOST_SCRIPT
    for key, value in replacements.items():
        script = script.replace(key, value)
    return script


class MetalRun:

    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.session = boto3.Session(profile_name=args.profile, region_name=args.region)
        self.ec2 = self.session.client("ec2")
        self.ssm = self.session.client("ssm")
        self.s3 = self.session.client("s3")
        self.sts = self.session.client("sts")
        self.iam = self.session.client("iam")
        self.arch: Optional[str] = None
        self.ami: Optional[str] = None
        self.bucket: Optional[str] = args.bucket
        self.instance_id: Optional[str] = None
        self.run_id = args.run_id or "%s-%s" % (time.strftime("%Y%m%d-%H%M%S", time.gmtime()), compact(args.instance_type))
        self.jars: Dict[str, str] = {}
        self.outdir = os.path.join(args.outdir, self.run_id)
        self.state_path = os.path.join(args.outdir, STATE_FILE)
        self.terminated = False
        self.launched_at: Optional[float] = None

    @property
    def s3_prefix(self) -> str:
        return "%s/%s/%s" % (self.args.prefix.strip("/"), self.arch, self.run_id)

    @property
    def s3_uri(self) -> str:
        return "s3://%s/%s" % (self.bucket, self.s3_prefix)

    @property
    def sides(self) -> List[str]:
        return ["baseline", "current"] if "baseline" in self.jars else ["current"]

    def ssm_run(self, commands: List[str], comment: str, timeout_seconds: int = 3600) -> Tuple[str, str]:
        sent = None
        for attempt in range(12):
            try:
                sent = self.ssm.send_command(
                    InstanceIds=[self.instance_id],
                    DocumentName="AWS-RunShellScript",
                    Comment=comment[:100],
                    Parameters={"commands": commands, "executionTimeout": [str(timeout_seconds)]},
                    TimeoutSeconds=120)
                break
            except ClientError as error:
                if error.response["Error"]["Code"] == "InvalidInstanceId" and attempt < 11:
                    time.sleep(10)
                    continue
                raise
        command_id = sent["Command"]["CommandId"]
        while True:
            time.sleep(3)
            try:
                invocation = self.ssm.get_command_invocation(CommandId=command_id, InstanceId=self.instance_id)
            except ClientError as error:
                if error.response["Error"]["Code"] == "InvocationDoesNotExist":
                    continue
                raise
            if invocation["Status"] not in ("Pending", "InProgress", "Delayed"):
                return invocation["Status"], invocation.get("StandardOutputContent", "") + invocation.get("StandardErrorContent", "")

    def instance_state(self) -> str:
        try:
            reservations = self.ec2.describe_instances(InstanceIds=[self.instance_id])["Reservations"]
            return reservations[0]["Instances"][0]["State"]["Name"]
        except (ClientError, IndexError, KeyError):
            return "unknown"

    def write_state(self, phase: str) -> None:
        os.makedirs(self.args.outdir, exist_ok=True)
        state = {"instance_id": self.instance_id, "region": self.args.region, "profile": self.args.profile,
                 "run_id": self.run_id, "bucket": self.bucket, "s3_uri": self.s3_uri, "outdir": self.outdir,
                 "phase": phase, "launched_at": self.launched_at}
        with open(self.state_path, "w", encoding="utf-8") as handle:
            json.dump(state, handle, indent=2)

    def clear_state(self) -> None:
        try:
            os.remove(self.state_path)
        except OSError:
            pass

    def preflight(self) -> None:
        log("preflight")
        account = self.sts.get_caller_identity()["Account"]
        self.bucket = self.bucket or "perf-comparison-temp-%s" % account
        try:
            self.s3.head_bucket(Bucket=self.bucket)
        except ClientError as error:
            fail("S3 bucket %s is not accessible (%s). Run `setup-infra` or pass --bucket."
                 % (self.bucket, error.response["Error"]["Code"]))
        try:
            self.iam.get_instance_profile(InstanceProfileName=self.args.instance_profile)
        except ClientError as error:
            if error.response["Error"]["Code"] == "NoSuchEntity":
                fail("instance profile %s does not exist. Run `setup-infra` or pass --instance-profile." % self.args.instance_profile)
            log("  warning: cannot read instance profile %s (%s); continuing" % (self.args.instance_profile, error.response["Error"]["Code"]))
        types = self.ec2.describe_instance_types(InstanceTypes=[self.args.instance_type])["InstanceTypes"]
        if not types:
            fail("unknown instance type %s" % self.args.instance_type)
        self.arch = "arm64" if "arm64" in types[0]["ProcessorInfo"]["SupportedArchitectures"] else "x86_64"
        if not types[0].get("BareMetal", False):
            log("  warning: %s is not a bare-metal instance type; expect run-to-run drift" % self.args.instance_type)
        if not self.ec2.describe_instance_type_offerings(
                Filters=[{"Name": "instance-type", "Values": [self.args.instance_type]}])["InstanceTypeOfferings"]:
            fail("%s is not offered in %s" % (self.args.instance_type, self.args.region))
        self.ami = self.ssm.get_parameter(Name=AMI_PARAMETERS[self.arch])["Parameter"]["Value"]
        if not self.args.subnet_id and not self.ec2.describe_vpcs(Filters=[{"Name": "isDefault", "Values": ["true"]}])["Vpcs"]:
            fail("no default VPC in %s; pass --subnet-id" % self.args.region)
        log("  account %s, region %s, %s (%s), AMI %s" % (account, self.args.region, self.args.instance_type, self.arch, self.ami))
        log("  bucket s3://%s, prefix %s" % (self.bucket, self.s3_prefix))
        log("  samples %d, baseline %s, e2e args %s" % (self.args.samples, "yes" if self.args.baseline_jar else "no",
                                                        shlex.join(self.args.e2e_arg_list) or "(none)"))
        cost = HOURLY_COST.get(self.args.instance_type)
        if cost:
            log("  cost about $%.2f/hour; dead-man shutdown after %.1f hours" % (cost, self.args.max_hours))

    def build(self) -> None:
        current = self.args.current_jar or E2E_JAR
        if not self.args.current_jar and not self.args.skip_build:
            log("building %s" % GRADLE_TASK)
            subprocess.run([os.path.join(PROJECT_ROOT, "gradlew"), "-q", GRADLE_TASK], cwd=PROJECT_ROOT, check=True)
        if not os.path.isfile(current):
            fail("e2e jar not found at %s" % current)
        self.jars["current"] = current
        if self.args.baseline_jar:
            if not os.path.isfile(self.args.baseline_jar):
                fail("baseline jar not found at %s" % self.args.baseline_jar)
            if os.path.samefile(self.args.baseline_jar, current):
                fail("--baseline-jar is the same file as the current jar")
            self.jars["baseline"] = self.args.baseline_jar
        for side, path in self.jars.items():
            log("  %s jar: %s (%.1f MB)" % (side, path, os.path.getsize(path) / 1e6))

    def stage(self) -> None:
        log("staging to %s/stage/" % self.s3_uri)
        for side, path in self.jars.items():
            self.s3.upload_file(path, self.bucket, "%s/stage/jars/%s.jar" % (self.s3_prefix, side))
        script = host_script(self.args, self.run_id, "%s/stage" % self.s3_uri, self.s3_uri, self.sides)
        self.s3.put_object(Bucket=self.bucket, Key="%s/stage/host.sh" % self.s3_prefix, Body=script.encode("utf-8"))
        os.makedirs(self.outdir, exist_ok=True)
        with open(os.path.join(self.outdir, "host.sh"), "w", encoding="utf-8") as handle:
            handle.write(script)

    def launch(self) -> None:
        root_device = self.ec2.describe_images(ImageIds=[self.ami])["Images"][0]["RootDeviceName"]
        minutes = max(1, int(self.args.max_hours * 60))
        params = dict(
            ImageId=self.ami, InstanceType=self.args.instance_type, MinCount=1, MaxCount=1,
            IamInstanceProfile={"Name": self.args.instance_profile},
            InstanceInitiatedShutdownBehavior="terminate",
            UserData="#!/bin/bash\nshutdown -h +%d 'smithy-java benchmark dead-man switch'\n" % minutes,
            MetadataOptions={"HttpTokens": "required", "HttpEndpoint": "enabled"},
            BlockDeviceMappings=[{"DeviceName": root_device,
                                  "Ebs": {"VolumeSize": 100, "VolumeType": "gp3", "DeleteOnTermination": True}}],
            TagSpecifications=[{"ResourceType": "instance", "Tags": [
                {"Key": "Name", "Value": "smithy-java-benchmark-%s" % self.run_id},
                {"Key": "ManagedBy", "Value": TAG_MANAGED_BY},
                {"Key": "RunId", "Value": self.run_id}]}],
        )
        if self.args.subnet_id or self.args.no_public_ip:
            interface = {"DeviceIndex": 0, "AssociatePublicIpAddress": not self.args.no_public_ip}
            if self.args.subnet_id:
                interface["SubnetId"] = self.args.subnet_id
            params["NetworkInterfaces"] = [interface]
        log("launching %s (no key pair, SSM only)" % self.args.instance_type)
        self.instance_id = self.ec2.run_instances(**params)["Instances"][0]["InstanceId"]
        self.launched_at = time.time()
        self.write_state("launched")
        log("  %s launched; state file %s" % (self.instance_id, self.state_path))

    def wait_for_ssm(self) -> None:
        log("waiting for the instance to boot and the SSM agent to come online (metal takes a while)")
        self.ec2.get_waiter("instance_running").wait(InstanceIds=[self.instance_id], WaiterConfig={"Delay": 15, "MaxAttempts": 80})
        deadline = time.time() + SSM_ONLINE_TIMEOUT_SECONDS
        while time.time() < deadline:
            info = self.ssm.describe_instance_information(
                Filters=[{"Key": "InstanceIds", "Values": [self.instance_id]}])["InstanceInformationList"]
            if info and info[0].get("PingStatus") == "Online":
                log("  SSM online after %d s" % (time.time() - self.launched_at))
                return
            state = self.instance_state()
            if state in ("shutting-down", "terminated", "stopped"):
                fail("instance %s is %s before SSM came online" % (self.instance_id, state))
            time.sleep(15)
        fail("SSM agent did not come online within %d minutes; check the instance profile and network egress"
             % (SSM_ONLINE_TIMEOUT_SECONDS // 60))

    def start_run(self) -> None:
        log("starting the host script (detached; progress is polled every %d s)" % self.args.poll_seconds)
        status, output = self.ssm_run([
            "set -e",
            "mkdir -p %s" % HOST_WORK_DIR,
            "aws s3 cp %s/stage/host.sh %s/host.sh" % (self.s3_uri, HOST_WORK_DIR),
            "chmod +x %s/host.sh" % HOST_WORK_DIR,
            "rm -f %s/DONE" % HOST_WORK_DIR,
            "nohup setsid %s/host.sh > %s/host.out 2>&1 < /dev/null &" % (HOST_WORK_DIR, HOST_WORK_DIR),
            "sleep 2",
            "pgrep -f '%s/host.sh' > /dev/null && echo 'host script started' || (cat %s/host.out; exit 1)"
            % (HOST_WORK_DIR, HOST_WORK_DIR),
        ], "smithy-java benchmark run", timeout_seconds=120)
        if status != "Success":
            fail("could not start the host script: %s" % output.strip())
        self.write_state("running")

    def await_run(self) -> int:
        printed = 0
        while True:
            status, output = self.ssm_run([
                "cat %s/DONE 2>/dev/null || true" % HOST_WORK_DIR,
                "echo ---PROGRESS---",
                "cat %s/progress.log 2>/dev/null || true" % HOST_WORK_DIR,
                "echo ---PROCESS---",
                "pgrep -f '%s/host.sh' > /dev/null && echo running || echo stopped" % HOST_WORK_DIR,
            ], "smithy-java benchmark poll", timeout_seconds=60)
            if status != "Success":
                state = self.instance_state()
                if state != "running":
                    fail("instance is %s; the dead-man switch (--max-hours) may have fired" % state)
                log("  poll returned %s; retrying" % status)
                time.sleep(self.args.poll_seconds)
                continue
            sentinel, _, rest = output.partition("---PROGRESS---")
            progress, _, process = rest.partition("---PROCESS---")
            lines = progress.strip().splitlines()
            for line in lines[printed:]:
                log("  host " + line)
            printed = len(lines)
            if sentinel.strip():
                failures = int(sentinel.strip())
                log("host run complete, %d failure(s)" % failures)
                return failures
            if "stopped" in process:
                _, diagnostics = self.ssm_run(["tail -n 40 %s/host.out 2>/dev/null" % HOST_WORK_DIR],
                                              "smithy-java benchmark diagnostics", timeout_seconds=60)
                fail("the host script died before writing DONE; host output:\n%s" % diagnostics)
            time.sleep(self.args.poll_seconds)

    @property
    def expected_results(self) -> int:
        return self.args.samples * len(self.sides)

    def retrieve(self) -> Tuple[str, int]:
        """Downloads the result files and returns the local directory and how many files arrived."""
        target = os.path.join(self.outdir, "results")
        log("retrieving %s/results/ to %s" % (self.s3_uri, target))
        prefix = "%s/results/" % self.s3_prefix
        count = 0
        for page in self.s3.get_paginator("list_objects_v2").paginate(Bucket=self.bucket, Prefix=prefix):
            for obj in page.get("Contents", []):
                relative = obj["Key"][len(prefix):]
                if not relative or relative.endswith("/"):
                    continue
                local = os.path.join(target, relative)
                os.makedirs(os.path.dirname(local), exist_ok=True)
                self.s3.download_file(self.bucket, obj["Key"], local)
                count += 1
        log("  %d of %d expected result file(s); logs stay under %s/logs/" % (count, self.expected_results, self.s3_uri))
        return target, count

    def retrieve_all(self) -> str:
        """Retrieves the results, retrying the host's upload once; keeps the instance if files are still missing."""
        target, count = self.retrieve()
        if count < self.expected_results:
            log("re-uploading from the host and retrying")
            self.ssm_run(["aws s3 cp --recursive --no-progress %s/results %s/results/" % (HOST_WORK_DIR, self.s3_uri)],
                         "smithy-java benchmark re-upload", timeout_seconds=600)
            target, count = self.retrieve()
        if count < self.expected_results:
            self.args.keep_instance = True
            fail("retrieved %d of %d result files. The instance is KEPT so the rest can be fetched from %s/results "
                 "on the host (SSM session); terminate it afterwards with `cleanup`. The dead-man switch still fires "
                 "after %.1f hours." % (count, self.expected_results, HOST_WORK_DIR, self.args.max_hours))
        return target

    def terminate(self) -> None:
        if self.terminated or not self.instance_id:
            return
        if self.args.keep_instance:
            log("KEEPING instance %s as requested. It costs money until you terminate it: "
                "python3 scripts/run-metal-benchmarks.py cleanup --instance-id %s --region %s"
                % (self.instance_id, self.instance_id, self.args.region))
            return
        log("terminating %s" % self.instance_id)
        try:
            self.ec2.terminate_instances(InstanceIds=[self.instance_id])
        except ClientError as error:
            if error.response["Error"]["Code"] != "InvalidInstanceID.NotFound":
                fail("termination of %s failed: %s; retained state at %s for `cleanup`"
                     % (self.instance_id, error, self.state_path))
            self.terminated = True
            self.clear_state()
            return
        for _ in range(12):
            if self.instance_state() in ("shutting-down", "terminated"):
                break
            time.sleep(5)
        state = self.instance_state()
        log("  %s is %s" % (self.instance_id, state))
        if state not in ("shutting-down", "terminated"):
            fail("termination of %s was not confirmed; retained state at %s for `cleanup`"
                 % (self.instance_id, self.state_path))
        self.terminated = True
        self.clear_state()

    def execute(self) -> int:
        self.install_signal_handlers()
        try:
            self.preflight()
            if self.args.dry_run:
                log("dry run: nothing launched. The host would run:")
                print(host_script(self.args, self.run_id, "%s/stage" % self.s3_uri, self.s3_uri,
                                  ["baseline", "current"] if self.args.baseline_jar else ["current"]))
                return 0
            self.build()
            self.stage()
            self.launch()
            self.wait_for_ssm()
            self.start_run()
            failures = self.await_run()
            results = self.retrieve_all()
            log("results: %s (also %s/)" % (results, self.s3_uri))
            if "baseline" in self.jars:
                log("compare: python3 scripts/compare-ocs.py --baseline %s/baseline/*.json --current %s/current/*.json "
                    "--out results/smithy-java/%s_ocs_results" % (results, results, compact(self.args.instance_type)))
            return 1 if failures else 0
        finally:
            self.terminate()

    def install_signal_handlers(self) -> None:
        def handler(signum, _frame):
            raise SystemExit("interrupted by signal %d" % signum)
        signal.signal(signal.SIGINT, handler)
        signal.signal(signal.SIGTERM, handler)


def load_state(outdir: str) -> Optional[dict]:
    path = os.path.join(outdir, STATE_FILE)
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def setup_infra(args: argparse.Namespace) -> int:
    session = boto3.Session(profile_name=args.profile, region_name=args.region)
    account = session.client("sts").get_caller_identity()["Account"]
    bucket = args.bucket or "perf-comparison-temp-%s" % account
    s3 = session.client("s3")
    iam = session.client("iam")
    role = args.instance_profile

    log("S3 bucket %s" % bucket)
    try:
        create = {"Bucket": bucket}
        if args.region != "us-east-1":
            create["CreateBucketConfiguration"] = {"LocationConstraint": args.region}
        s3.create_bucket(**create)
        log("  created")
    except ClientError as error:
        if error.response["Error"]["Code"] not in ("BucketAlreadyOwnedByYou", "BucketAlreadyExists"):
            raise
        log("  exists")
    s3.put_public_access_block(Bucket=bucket, PublicAccessBlockConfiguration={
        "BlockPublicAcls": True, "IgnorePublicAcls": True, "BlockPublicPolicy": True, "RestrictPublicBuckets": True})

    log("IAM role %s (EC2 trust, SSM core, read/write on the bucket)" % role)
    trust = {"Version": "2012-10-17", "Statement": [{
        "Effect": "Allow", "Principal": {"Service": "ec2.amazonaws.com"}, "Action": "sts:AssumeRole"}]}
    try:
        iam.create_role(RoleName=role, AssumeRolePolicyDocument=json.dumps(trust),
                        Description="smithy-java benchmark host: SSM + results bucket",
                        Tags=[{"Key": "ManagedBy", "Value": TAG_MANAGED_BY}])
        log("  created")
    except ClientError as error:
        if error.response["Error"]["Code"] != "EntityAlreadyExists":
            raise
        log("  exists")
    iam.attach_role_policy(RoleName=role, PolicyArn=SSM_MANAGED_POLICY)
    bucket_policy = {"Version": "2012-10-17", "Statement": [
        {"Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"], "Resource": "arn:aws:s3:::%s/*" % bucket},
        {"Effect": "Allow", "Action": ["s3:ListBucket", "s3:GetBucketLocation"], "Resource": "arn:aws:s3:::%s" % bucket},
    ]}
    iam.put_role_policy(RoleName=role, PolicyName="SmithyJavaBenchmarkBucketAccess", PolicyDocument=json.dumps(bucket_policy))

    log("instance profile %s" % role)
    try:
        iam.create_instance_profile(InstanceProfileName=role, Tags=[{"Key": "ManagedBy", "Value": TAG_MANAGED_BY}])
        log("  created")
    except ClientError as error:
        if error.response["Error"]["Code"] != "EntityAlreadyExists":
            raise
        log("  exists")
    try:
        iam.add_role_to_instance_profile(InstanceProfileName=role, RoleName=role)
    except ClientError as error:
        if error.response["Error"]["Code"] != "LimitExceeded":
            raise
    log("done. IAM changes take about ten seconds to propagate before the first launch.")
    log("run: python3 scripts/run-metal-benchmarks.py --bucket %s --instance-profile %s --dry-run" % (bucket, role))
    return 0


def main(argv: List[str]) -> int:
    args = parse_args(argv)
    if args.command == "setup-infra":
        return setup_infra(args)
    if args.command == "cleanup":
        state = load_state(args.outdir)
        instance_id = args.instance_id or (state or {}).get("instance_id")
        if not instance_id:
            fail("no --instance-id and no state file at %s" % os.path.join(args.outdir, STATE_FILE))
        if state and not args.instance_id:
            args.region = state.get("region", args.region)
            args.profile = state.get("profile", args.profile)
        run = MetalRun(args)
        run.instance_id = instance_id
        run.args.keep_instance = False
        run.terminate()
        return 0
    existing = load_state(args.outdir)
    if existing and not args.dry_run:
        fail("a run is already recorded in %s (instance %s, results at %s). Run `cleanup` to terminate it first."
             % (os.path.join(args.outdir, STATE_FILE), existing.get("instance_id"), existing.get("s3_uri")))
    return MetalRun(args).execute()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
