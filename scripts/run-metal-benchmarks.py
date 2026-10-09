#!/usr/bin/env python3
"""Run the smithy-java benchmarks on a bare-metal EC2 host, end to end, and leave the results in S3.

Implements the metal procedure from AwsSdkPerformanceBenchmarkModels results/ocs-sop.md with no SSH, no
key pair and no inbound network access: the instance is driven with SSM Run Command, and every artifact
moves through S3 (jars and the host script up, results down).

    preflight -> build jars -> stage to S3 -> launch -> wait for SSM -> bootstrap -> smoke test
             -> detached run (interleaved samples) -> poll -> retrieve -> compare -> terminate

Subcommands (run is the default):
  run          the whole cycle above
  setup-infra  one-time, idempotent: S3 bucket, IAM role and instance profile (SSM + bucket access)
  resume       reattach to the instance recorded in the state file and finish the cycle
  cleanup      terminate the instance recorded in the state file (or --instance-id)

Cost: m7i.metal-24xl is about $4.84/hour and m7g.metal about $2.61/hour. The instance is terminated in a
finally block and on SIGINT/SIGTERM, its id is written to <outdir>/metal-run-state.json the moment it
launches, it shuts itself down after --max-hours as a dead-man switch, and `cleanup` finishes the job
after a crash. Check the EC2 console anyway after an abnormal exit.

Examples:
  python3 scripts/run-metal-benchmarks.py setup-infra
  python3 scripts/run-metal-benchmarks.py --dry-run
  python3 scripts/run-metal-benchmarks.py --baseline-jar /tmp/baseline/smithy-java-e2e-benchmark.jar
  python3 scripts/run-metal-benchmarks.py --instance-type m7g.metal --suite e2e,serde --samples 3
  python3 scripts/run-metal-benchmarks.py --suite fixture,h1scaling --samples 5 \
      --h1-baseline-jmh-jar ../smithy-java-before/http/http-client/build/libs/http-client-1.7.0-jmh.jar
  python3 scripts/run-metal-benchmarks.py resume

Suites: e2e (the cross-SDK ops/CPU-sec loop), serde (JMH serialization), fixture (stub vs real transport
against the Java fixture server) and h1scaling (the http-client module's JMH concurrency
benchmark against its Netty server, current jar vs an optional baseline jar, platform and virtual workers).
"""

import argparse
import glob
import json
import math
import os
import re
import shlex
import shutil
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
HOST_SCRIPT = os.path.join(PROJECT_ROOT, "scripts", "metal-host.sh")
E2E_JAR = os.path.join(PROJECT_ROOT, "benchmarks", "e2e-benchmarks", "build", "libs", "smithy-java-e2e-benchmark.jar")
SERDE_JAR_GLOB = os.path.join(PROJECT_ROOT, "benchmarks", "serde-benchmarks", "build", "libs", "*-jmh.jar")
H1_JMH_JAR_GLOB = os.path.join(PROJECT_ROOT, "http", "http-client", "build", "libs", "*-jmh.jar")
H1_SERVER_JAR_GLOB = os.path.join(PROJECT_ROOT, "http", "http-client", "build", "libs", "*-jmh-server.jar")
GRADLE_TASKS = {
    "e2e": [":benchmarks:e2e-benchmarks:shadowJar"],
    "serde": [":benchmarks:serde-benchmarks:jmhJar"],
    # The fixture experiment runs the e2e jar against the Java fixture server; both are built here and staged.
    "fixture": [":benchmarks:e2e-benchmarks:shadowJar", ":benchmarks:e2e-benchmarks:fixtureServerJar"],
    # Concurrent-caller check for client transport changes: the http-client module's H1ScalingBenchmark
    # (JMH) against its Netty BenchmarkServer, current jar vs an optional baseline jar.
    "h1scaling": [":http:http-client:jmhJar", ":http:http-client:jmhServerJar"],
}
FIXTURE_SERVER_JAR = os.path.join(PROJECT_ROOT, "benchmarks", "e2e-benchmarks", "build", "libs", "smithy-java-fixture-server.jar")
FIXTURE_DIR = os.path.join(PROJECT_ROOT, "benchmarks", "e2e-benchmarks", "fixture")

DEFAULT_REGION = "us-east-1"
DEFAULT_INSTANCE_TYPE = "m7i.metal-24xl"
DEFAULT_INSTANCE_PROFILE = "SmithyJavaBenchmarkHost"
DEFAULT_PREFIX = "ops-cpusec/smithy-java"
DEFAULT_OUTDIR = os.path.join(PROJECT_ROOT, "build", "metal-runs")
HOST_WORK_DIR = "/opt/smithy-java-benchmark"
STATE_FILE = "metal-run-state.json"
TAG_MANAGED_BY = "smithy-java-run-metal-benchmarks"
SSM_MANAGED_POLICY = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"

# Amazon Linux 2023, resolved from the public SSM parameters so no AMI id is hard-coded.
AMI_PARAMETERS = {
    "x86_64": "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64",
    "arm64": "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-arm64",
}
HOURLY_COST = {"m7i.metal-24xl": 4.84, "m7g.metal": 2.61}

# Metal hosts take several minutes to boot and register with SSM.
SSM_ONLINE_TIMEOUT_SECONDS = 25 * 60
# A smoke test outside this CPU/wall band means a misconfigured or contended host, not an SDK result.
SMOKE_CPU_WALL_RANGE = (0.75, 1.25)


def log(message: str) -> None:
    print("[%s] %s" % (time.strftime("%H:%M:%S"), message), flush=True)


def newest(pattern: str, what: str) -> str:
    """The most recently modified file matching a glob, or exit with a message naming what was expected."""
    candidates = sorted(glob.glob(pattern), key=os.path.getmtime)
    if not candidates:
        fail("%s not found (%s)" % (what, pattern))
    return candidates[-1]


def fail(message: str) -> None:
    raise SystemExit("error: " + message)


def compact(instance_type: str) -> str:
    """m7i.metal-24xl -> m7imetal24xl, the SOP's results file prefix."""
    return re.sub(r"[^a-z0-9]", "", instance_type.lower())


def parse_args(argv: List[str]) -> argparse.Namespace:
    command = "run"
    if argv and argv[0] in ("run", "setup-infra", "resume", "cleanup"):
        command = argv.pop(0)

    parser = argparse.ArgumentParser(
        prog="run-metal-benchmarks.py [run|setup-infra|resume|cleanup]",
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter)
    aws = parser.add_argument_group("AWS")
    aws.add_argument("--region", default=DEFAULT_REGION, help="region to launch in (default %(default)s, per the SOP)")
    aws.add_argument("--profile", default=None, help="AWS credentials profile (default: the environment's)")
    aws.add_argument("--bucket", default=None,
                     help="S3 bucket for staging and results (default perf-comparison-temp-<account-id>)")
    aws.add_argument("--prefix", default=DEFAULT_PREFIX,
                     help="S3 key prefix; <arch>/<run-id>/ is appended (default %(default)s)")
    aws.add_argument("--instance-profile", default=DEFAULT_INSTANCE_PROFILE,
                     help="IAM instance profile with SSM core + bucket access (default %(default)s)")

    host = parser.add_argument_group("host")
    host.add_argument("--instance-type", default=DEFAULT_INSTANCE_TYPE,
                      help="m7i.metal-24xl (x86) or m7g.metal (Graviton); default %(default)s")
    host.add_argument("--subnet-id", default=None, help="launch into this subnet instead of the default VPC")
    host.add_argument("--security-group-id", default=None, help="security group to attach (no ingress is needed)")
    host.add_argument("--no-public-ip", action="store_true",
                      help="do not associate a public IP; the subnet then needs NAT or SSM/S3 VPC endpoints")
    host.add_argument("--java-major", type=int, default=25, help="Corretto major version to install (default %(default)s)")
    host.add_argument("--max-hours", type=float, default=6.0,
                      help="dead-man switch: the host shuts itself down after this many hours (default %(default)s)")

    bench = parser.add_argument_group("benchmarks")
    bench.add_argument("--suite", default="e2e",
                       help="comma-separated: e2e, serde, fixture, h1scaling (default %(default)s)")
    bench.add_argument("--samples", type=int, default=3,
                       help="e2e samples per side, interleaved baseline/current (default %(default)s)")
    bench.add_argument("--baseline-jar", default=None,
                       help="e2e jar built from the baseline commit; enables the baseline side and `compare`")
    bench.add_argument("--current-jar", default=None, help="e2e jar to measure (default: build it)")
    bench.add_argument("--e2e-args", default="", help="extra arguments for every e2e run, e.g. \"--min-measure-cpu-seconds 2\"")
    bench.add_argument("--serde-args", default="", help="extra JMH arguments for the serde suite, e.g. \"-p testCaseId=x\"")
    bench.add_argument("--serde-fast", action="store_true", help="serde: 1 warmup and 3 measurement iterations")
    bench.add_argument("--fixture-benchmarks", default="rpcv2Cbor_PutItemRequest_Baseline,awsJson1_0_GetItemOutput_M,restXml_PutObject_L,restXml_GetObject_L",
                       help="fixture suite: comma-separated e2e benchmark ids")
    bench.add_argument("--fixture-modes", default="stub,https",
                       help="fixture suite: comma-separated experiment modes (stub, http, https)")
    bench.add_argument("--fixture-server-cpus", default="2", help="fixture suite: taskset list for the server")
    bench.add_argument("--fixture-client-cpus", default="4-11", help="fixture suite: taskset list for the client JVM")
    bench.add_argument("--h1-baseline-jmh-jar", default=None,
                       help="h1scaling suite: a second http-client JMH jar (e.g. built from main) to run as the baseline")
    bench.add_argument("--h1-concurrency", default="1,10,100", help="h1scaling suite: JMH -p concurrency list")
    bench.add_argument("--h1-max-connections", default="100", help="h1scaling suite: JMH -p maxConnections list")
    bench.add_argument("--h1-threads", default="platform,virtual",
                       help="h1scaling suite: worker thread kinds to run (platform and/or virtual)")
    bench.add_argument("--h1-includes", default="H1ScalingBenchmark.h1Smithy", help="h1scaling suite: JMH benchmark regex")
    bench.add_argument("--h1-server-cpus", default="12-19", help="h1scaling suite: taskset list for the Netty server")
    bench.add_argument("--h1-client-cpus", default="24-47", help="h1scaling suite: taskset list for the JMH JVM")
    bench.add_argument("--h1-fast", action="store_true", help="h1scaling suite: short warmup and measurement iterations")
    bench.add_argument("--lang", default="smithy-java", help="SDK label written by `compare` (default %(default)s)")
    bench.add_argument("--skip-build", action="store_true", help="use the jars already in build/libs")
    bench.add_argument("--skip-smoke", action="store_true", help="skip the smoke test")
    bench.add_argument("--ignore-smoke-failure", action="store_true", help="continue even if the smoke test fails")

    flow = parser.add_argument_group("flow")
    flow.add_argument("--outdir", default=DEFAULT_OUTDIR, help="local directory for results and state (default %(default)s)")
    flow.add_argument("--run-id", default=None, help="override the generated run id")
    flow.add_argument("--poll-seconds", type=int, default=60, help="how often to poll the host (default %(default)s)")
    flow.add_argument("--keep-instance", action="store_true", help="do not terminate the instance at the end")
    flow.add_argument("--instance-id", default=None, help="cleanup: instance to terminate instead of the state file's")
    flow.add_argument("--dry-run", action="store_true", help="validate and print the plan without launching anything")

    args = parser.parse_args(argv)
    args.command = command
    args.suites = [s.strip() for s in args.suite.split(",") if s.strip()]
    for suite in args.suites:
        if suite not in GRADLE_TASKS:
            parser.error("unknown suite '%s'; expected a comma-separated subset of %s" % (suite, ", ".join(GRADLE_TASKS)))
    if not args.suites:
        parser.error("--suite must name at least one suite")
    if args.samples < 1:
        parser.error("--samples must be at least 1")
    if not math.isfinite(args.max_hours) or args.max_hours <= 0:
        parser.error("--max-hours must be finite and positive")
    if args.poll_seconds < 1:
        parser.error("--poll-seconds must be at least 1")
    if args.baseline_jar and not ({"e2e", "fixture"} & set(args.suites)):
        parser.error("--baseline-jar only applies to the e2e or fixture suite")
    if args.h1_baseline_jmh_jar and "h1scaling" not in args.suites:
        parser.error("--h1-baseline-jmh-jar only applies to the h1scaling suite")
    if "fixture" in args.suites and "e2e" in args.suites:
        parser.error("run the fixture suite without e2e; it reuses --samples as runs per mode")
    return args


class MetalRun:
    """One benchmark run on one freshly launched metal instance."""

    def __init__(self, args: argparse.Namespace):
        self.args = args
        self.session = boto3.Session(profile_name=args.profile, region_name=args.region)
        self.ec2 = self.session.client("ec2")
        self.ssm = self.session.client("ssm")
        self.s3 = self.session.client("s3")
        self.sts = self.session.client("sts")
        self.iam = self.session.client("iam")
        self.account: Optional[str] = None
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

    # ----------------------------------------------------------------------------------------- helpers

    @property
    def s3_prefix(self) -> str:
        return "%s/%s/%s" % (self.args.prefix.strip("/"), self.arch, self.run_id)

    @property
    def s3_uri(self) -> str:
        return "s3://%s/%s" % (self.bucket, self.s3_prefix)

    def ssm_run(self, commands: List[str], comment: str, timeout_seconds: int = 3600, echo: bool = True) -> Tuple[str, str, str]:
        """Runs shell commands on the host with SSM Run Command and waits for them. Returns status, stdout, stderr."""
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
                    time.sleep(10)  # the agent has not finished registering yet
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
            status = invocation["Status"]
            if status not in ("Pending", "InProgress", "Delayed"):
                break
        stdout = invocation.get("StandardOutputContent", "")
        stderr = invocation.get("StandardErrorContent", "")
        if echo:
            for line in (stdout + ("\n" + stderr if stderr.strip() else "")).rstrip().splitlines():
                print("    " + line, flush=True)
        return status, stdout, stderr

    def instance_state(self) -> str:
        try:
            reservations = self.ec2.describe_instances(InstanceIds=[self.instance_id])["Reservations"]
            return reservations[0]["Instances"][0]["State"]["Name"]
        except (ClientError, IndexError, KeyError):
            return "unknown"

    def write_state(self, phase: str) -> None:
        os.makedirs(self.args.outdir, exist_ok=True)
        state = {
            "instance_id": self.instance_id,
            "region": self.args.region,
            "profile": self.args.profile,
            "run_id": self.run_id,
            "arch": self.arch,
            "bucket": self.bucket,
            "prefix": self.args.prefix,
            "instance_type": self.args.instance_type,
            "outdir": self.args.outdir,
            "suites": self.args.suites,
            "baseline": bool(self.args.baseline_jar),
            "current_jar": self.jars.get("current"),
            "lang": self.args.lang,
            "poll_seconds": self.args.poll_seconds,
            "keep_instance": self.args.keep_instance,
            "phase": phase,
            "launched_at": self.launched_at,
        }
        with open(self.state_path, "w", encoding="utf-8") as handle:
            json.dump(state, handle, indent=2)

    def clear_state(self) -> None:
        try:
            os.remove(self.state_path)
        except OSError:
            pass

    # ----------------------------------------------------------------------------------------- phases

    def preflight(self) -> None:
        log("preflight")
        self.account = self.sts.get_caller_identity()["Account"]
        self.bucket = self.bucket or "perf-comparison-temp-%s" % self.account
        try:
            self.s3.head_bucket(Bucket=self.bucket)
        except ClientError as error:
            fail("S3 bucket %s is not accessible (%s). Run `setup-infra` or pass --bucket."
                 % (self.bucket, error.response["Error"]["Code"]))
        try:
            self.iam.get_instance_profile(InstanceProfileName=self.args.instance_profile)
        except ClientError as error:
            if error.response["Error"]["Code"] == "NoSuchEntity":
                fail("instance profile %s does not exist. Run `setup-infra` or pass --instance-profile."
                     % self.args.instance_profile)
            log("  warning: cannot read instance profile %s (%s); continuing"
                % (self.args.instance_profile, error.response["Error"]["Code"]))

        types = self.ec2.describe_instance_types(InstanceTypes=[self.args.instance_type])["InstanceTypes"]
        if not types:
            fail("unknown instance type %s" % self.args.instance_type)
        architectures = types[0]["ProcessorInfo"]["SupportedArchitectures"]
        self.arch = "arm64" if "arm64" in architectures else "x86_64"
        if not types[0].get("BareMetal", False):
            log("  warning: %s is not a bare-metal instance type; expect run-to-run drift" % self.args.instance_type)
        offerings = self.ec2.describe_instance_type_offerings(
            Filters=[{"Name": "instance-type", "Values": [self.args.instance_type]}])["InstanceTypeOfferings"]
        if not offerings:
            fail("%s is not offered in %s" % (self.args.instance_type, self.args.region))
        self.ami = self.ssm.get_parameter(Name=AMI_PARAMETERS[self.arch])["Parameter"]["Value"]
        if not self.args.subnet_id:
            vpcs = self.ec2.describe_vpcs(Filters=[{"Name": "isDefault", "Values": ["true"]}])["Vpcs"]
            if not vpcs:
                fail("no default VPC in %s; pass --subnet-id (and --security-group-id)" % self.args.region)
        if not os.path.isfile(HOST_SCRIPT):
            fail("missing host script %s" % HOST_SCRIPT)

        log("  account %s, region %s, %s (%s), AMI %s" % (self.account, self.args.region, self.args.instance_type,
                                                          self.arch, self.ami))
        log("  bucket s3://%s, prefix %s" % (self.bucket, self.s3_prefix))
        log("  suites %s, samples %d, baseline %s" % (",".join(self.args.suites), self.args.samples,
                                                      "yes" if self.args.baseline_jar else "no"))
        cost = HOURLY_COST.get(self.args.instance_type)
        if cost:
            log("  cost about $%.2f/hour; dead-man shutdown after %.1f hours" % (cost, self.args.max_hours))

    def build(self) -> None:
        if not self.args.skip_build:
            tasks = [t for s in self.args.suites if not (s == "e2e" and self.args.current_jar) for t in GRADLE_TASKS[s]]
            if tasks:
                log("building %s" % " ".join(tasks))
                subprocess.run([os.path.join(PROJECT_ROOT, "gradlew"), "-q"] + tasks, cwd=PROJECT_ROOT, check=True)
        if "h1scaling" in self.args.suites:
            self.jars["h1-jmh"] = newest(H1_JMH_JAR_GLOB, "http-client JMH jar")
            self.jars["h1-server"] = newest(H1_SERVER_JAR_GLOB, "http-client benchmark server jar")
            if self.args.h1_baseline_jmh_jar:
                if not os.path.isfile(self.args.h1_baseline_jmh_jar):
                    fail("baseline JMH jar not found at %s" % self.args.h1_baseline_jmh_jar)
                if os.path.samefile(self.args.h1_baseline_jmh_jar, self.jars["h1-jmh"]):
                    fail("--h1-baseline-jmh-jar is the same file as the current JMH jar")
                self.jars["h1-jmh-baseline"] = self.args.h1_baseline_jmh_jar
        if "fixture" in self.args.suites:
            current = self.args.current_jar or E2E_JAR
            if not os.path.isfile(current):
                fail("e2e jar not found at %s" % current)
            self.jars["current"] = current
            if not os.path.isfile(FIXTURE_SERVER_JAR):
                fail("fixture server jar not found at %s" % FIXTURE_SERVER_JAR)
            # Staged flat alongside the jars; metal-host.sh copies the two helpers into the host's fixture dir.
            self.jars["fixture-server.jar"] = FIXTURE_SERVER_JAR
            # With a baseline jar, the fixture experiment runs both the old and current clients (stub + https).
            if self.args.baseline_jar:
                if not os.path.isfile(self.args.baseline_jar):
                    fail("baseline jar not found at %s" % self.args.baseline_jar)
                self.jars["baseline"] = self.args.baseline_jar
            self.jars["run-transport.py"] = os.path.join(FIXTURE_DIR, "run-transport.py")
            self.jars["make-cert.sh"] = os.path.join(FIXTURE_DIR, "make-cert.sh")
        if "e2e" in self.args.suites:
            current = self.args.current_jar or E2E_JAR
            if not os.path.isfile(current):
                fail("e2e jar not found at %s" % current)
            self.jars["current"] = current
            if self.args.baseline_jar:
                if not os.path.isfile(self.args.baseline_jar):
                    fail("baseline jar not found at %s" % self.args.baseline_jar)
                if os.path.samefile(self.args.baseline_jar, current):
                    fail("--baseline-jar is the same file as the current jar")
                self.jars["baseline"] = self.args.baseline_jar
        if "serde" in self.args.suites:
            self.jars["serde"] = newest(SERDE_JAR_GLOB, "serde JMH jar")
        for role, path in self.jars.items():
            log("  %s jar: %s (%.1f MB)" % (role, path, os.path.getsize(path) / 1e6))

    @staticmethod
    def _staged_name(role: str) -> str:
        return role if "." in role else "%s.jar" % role

    def stage(self) -> None:
        log("staging to %s/stage/" % self.s3_uri)
        self.s3.upload_file(HOST_SCRIPT, self.bucket, "%s/stage/metal-host.sh" % self.s3_prefix)
        for role, path in self.jars.items():
            # Everything under stage/jars/ is downloaded to the host's jars/ directory by the bootstrap.
            name = self._staged_name(role)
            self.s3.upload_file(path, self.bucket, "%s/stage/jars/%s" % (self.s3_prefix, name))
            log("  uploaded %s" % name)

    def launch(self) -> None:
        root_device = self.ec2.describe_images(ImageIds=[self.ami])["Images"][0]["RootDeviceName"]
        minutes = max(1, int(self.args.max_hours * 60))
        user_data = "#!/bin/bash\nshutdown -h +%d 'smithy-java benchmark dead-man switch'\n" % minutes
        params = dict(
            ImageId=self.ami,
            InstanceType=self.args.instance_type,
            MinCount=1,
            MaxCount=1,
            IamInstanceProfile={"Name": self.args.instance_profile},
            InstanceInitiatedShutdownBehavior="terminate",
            UserData=user_data,
            MetadataOptions={"HttpTokens": "required", "HttpEndpoint": "enabled"},
            BlockDeviceMappings=[{
                "DeviceName": root_device,
                "Ebs": {"VolumeSize": 100, "VolumeType": "gp3", "DeleteOnTermination": True},
            }],
            TagSpecifications=[{
                "ResourceType": "instance",
                "Tags": [
                    {"Key": "Name", "Value": "smithy-java-benchmark-%s" % self.run_id},
                    {"Key": "ManagedBy", "Value": TAG_MANAGED_BY},
                    {"Key": "RunId", "Value": self.run_id},
                ],
            }],
        )
        if self.args.subnet_id or self.args.security_group_id or self.args.no_public_ip:
            interface = {"DeviceIndex": 0, "AssociatePublicIpAddress": not self.args.no_public_ip}
            if self.args.subnet_id:
                interface["SubnetId"] = self.args.subnet_id
            if self.args.security_group_id:
                interface["Groups"] = [self.args.security_group_id]
            params["NetworkInterfaces"] = [interface]

        log("launching %s (no key pair, SSM only)" % self.args.instance_type)
        response = self.ec2.run_instances(**params)
        self.instance_id = response["Instances"][0]["InstanceId"]
        self.launched_at = time.time()
        self.write_state("launched")
        log("  %s launched; state file %s" % (self.instance_id, self.state_path))

    def wait_for_ssm(self) -> None:
        log("waiting for the instance to boot and the SSM agent to come online (metal takes a while)")
        self.ec2.get_waiter("instance_running").wait(
            InstanceIds=[self.instance_id], WaiterConfig={"Delay": 15, "MaxAttempts": 80})
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

    def bootstrap(self) -> Dict[str, str]:
        log("bootstrapping the host")
        stage = "%s/stage" % self.s3_uri
        status, stdout, _ = self.ssm_run([
            "set -e",
            "mkdir -p %s" % HOST_WORK_DIR,
            "aws s3 cp %s/metal-host.sh %s/metal-host.sh" % (stage, HOST_WORK_DIR),
            "chmod +x %s/metal-host.sh" % HOST_WORK_DIR,
            "%s/metal-host.sh bootstrap %s %s %d" % (HOST_WORK_DIR, HOST_WORK_DIR, stage, self.args.java_major),
        ], "smithy-java benchmark bootstrap", timeout_seconds=1800)
        if status != "Success":
            fail("bootstrap ended with status %s" % status)
        readiness = parse_kv_line(stdout, "READINESS")
        if not readiness:
            fail("bootstrap did not print a READINESS line")
        expected_jars = sum(1 for role in self.jars if self._staged_name(role).endswith(".jar"))
        if int(readiness.get("jars", "0")) < expected_jars:
            fail("expected %d jars on the host, found %s" % (expected_jars, readiness.get("jars")))
        if self.arch == "x86_64" and readiness.get("governor") != "performance":
            log("  warning: CPU governor is %s, not performance" % readiness.get("governor"))
        return readiness

    def smoke(self) -> None:
        if self.args.skip_smoke or "current" not in self.jars:
            return
        log("smoke test: one e2e benchmark at default settings")
        status, stdout, _ = self.ssm_run(
            ["%s/metal-host.sh smoke %s %s current.jar" % (HOST_WORK_DIR, HOST_WORK_DIR, self.args.instance_type)],
            "smithy-java benchmark smoke test", timeout_seconds=1800)
        result = parse_kv_line(stdout, "SMOKE")
        problems = []
        if status != "Success" or result.get("status") != "ok":
            problems.append("the smoke benchmark failed")
        else:
            ratio = float(result.get("cpu_wall", "0"))
            if not SMOKE_CPU_WALL_RANGE[0] <= ratio <= SMOKE_CPU_WALL_RANGE[1]:
                problems.append("CPU/wall ratio %.3f outside %s; the host is contended or misconfigured"
                                % (ratio, SMOKE_CPU_WALL_RANGE))
            if result.get("xbatch") != "True":
                problems.append("the benchmark JVM did not run with -Xbatch")
        if problems:
            message = "smoke test: " + "; ".join(problems)
            if self.args.ignore_smoke_failure:
                log("  warning: " + message)
            else:
                fail(message + " (use --ignore-smoke-failure to continue anyway)")

    def start_run(self) -> None:
        log("starting the benchmark matrix (detached on the host; progress is polled every %d s)" % self.args.poll_seconds)
        command = " ".join([
            "%s/metal-host.sh" % HOST_WORK_DIR, "run", HOST_WORK_DIR, self.s3_uri, self.args.instance_type,
            ",".join(self.args.suites), str(self.args.samples),
            "current.jar" if "current" in self.jars else "-",
            "baseline.jar" if "baseline" in self.jars else "-",
            "serde.jar" if "serde" in self.jars else "-",
            "1" if self.args.serde_fast else "0",
            shlex.quote(self.args.e2e_args),
            shlex.quote(self.args.serde_args),
        ])
        env = ""
        if "fixture" in self.args.suites:
            env = " ".join([
                "FIXTURE_BENCHMARKS=%s" % shlex.quote(self.args.fixture_benchmarks),
                "FIXTURE_RUNS=%d" % self.args.samples,
                "FIXTURE_SERVER_CPUS=%s" % shlex.quote(self.args.fixture_server_cpus),
                "FIXTURE_CLIENT_CPUS=%s" % shlex.quote(self.args.fixture_client_cpus),
                "FIXTURE_MODES=%s" % shlex.quote(self.args.fixture_modes),
            ]) + " "
        if "h1scaling" in self.args.suites:
            env += " ".join([
                "H1_CONCURRENCY=%s" % shlex.quote(self.args.h1_concurrency),
                "H1_MAX_CONNECTIONS=%s" % shlex.quote(self.args.h1_max_connections),
                "H1_THREADS=%s" % shlex.quote(self.args.h1_threads),
                "H1_INCLUDES=%s" % shlex.quote(self.args.h1_includes),
                "H1_SERVER_CPUS=%s" % shlex.quote(self.args.h1_server_cpus),
                "H1_CLIENT_CPUS=%s" % shlex.quote(self.args.h1_client_cpus),
                "H1_FAST=%s" % ("1" if self.args.h1_fast else "0"),
            ]) + " "
        status, _, _ = self.ssm_run([
            "rm -f %s/DONE" % HOST_WORK_DIR,
            "%snohup setsid %s > %s/host-run.out 2>&1 < /dev/null &" % (env, command, HOST_WORK_DIR),
            "sleep 2",
            "pgrep -f 'metal-host.sh run' > /dev/null && echo 'host runner started' || (cat %s/host-run.out; exit 1)"
            % HOST_WORK_DIR,
        ], "smithy-java benchmark run", timeout_seconds=120)
        if status != "Success":
            fail("could not start the benchmark run on the host")
        self.write_state("running")

    def await_run(self) -> int:
        printed = 0
        while True:
            status, stdout, _ = self.ssm_run([
                "cat %s/DONE 2>/dev/null || true" % HOST_WORK_DIR,
                "echo ---PROGRESS---",
                "cat %s/progress.log 2>/dev/null || true" % HOST_WORK_DIR,
                "echo ---PROCESS---",
                "pgrep -f 'metal-host.sh run' > /dev/null && echo running || echo stopped",
            ], "smithy-java benchmark poll", timeout_seconds=60, echo=False)
            if status != "Success":
                state = self.instance_state()
                if state != "running":
                    fail("instance is %s; the dead-man switch (--max-hours) may have fired" % state)
                log("  poll returned %s; retrying" % status)
                time.sleep(self.args.poll_seconds)
                continue
            sentinel, _, rest = stdout.partition("---PROGRESS---")
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
                log("the host runner is no longer running and left no DONE sentinel; host output follows")
                self.ssm_run([
                    "echo '--- host-run.out ---'; tail -n 40 %s/host-run.out 2>/dev/null" % HOST_WORK_DIR,
                    "echo '--- progress.log ---'; tail -n 20 %s/progress.log 2>/dev/null" % HOST_WORK_DIR,
                    "for f in %s/logs/*.log; do echo \"--- $f ---\"; tail -n 15 \"$f\"; done 2>/dev/null" % HOST_WORK_DIR,
                ], "smithy-java benchmark failure diagnostics", timeout_seconds=60)
                fail("host runner died before writing the DONE sentinel (see the host output above)")
            time.sleep(self.args.poll_seconds)

    def retrieve(self) -> str:
        prefix = "%s/results/" % self.s3_prefix
        target = os.path.join(self.outdir, "results")
        log("retrieving %s/results/ to %s" % (self.s3_uri, target))
        count = 0
        paginator = self.s3.get_paginator("list_objects_v2")
        for page in paginator.paginate(Bucket=self.bucket, Prefix=prefix):
            for obj in page.get("Contents", []):
                relative = obj["Key"][len(prefix):]
                if not relative or relative.endswith("/"):
                    continue
                local = os.path.join(target, relative)
                os.makedirs(os.path.dirname(local), exist_ok=True)
                self.s3.download_file(self.bucket, obj["Key"], local)
                count += 1
        log("  %d file(s)" % count)
        if count == 0:
            fail("no results were uploaded; the host run may have failed before it got that far")
        return target

    def compare(self, results_dir: str) -> None:
        baseline = os.path.join(results_dir, "e2e", "baseline")
        current = os.path.join(results_dir, "e2e", "current")
        if not (os.path.isdir(baseline) and os.path.isdir(current)):
            if os.path.isdir(current):
                log("no baseline side; skipping compare (pass --baseline-jar to produce the cross-SDK files)")
            return
        current_jar = self.jars.get("current")
        if not current_jar or not os.path.isfile(current_jar):
            log("warning: the current e2e jar is not available locally; skipping compare")
            return
        out_prefix = os.path.join(self.outdir, "%s_ocs_results" % compact(self.args.instance_type))
        java = os.path.join(os.environ["JAVA_HOME"], "bin", "java") if os.environ.get("JAVA_HOME") else shutil.which("java")
        if not java:
            log("warning: no local java to run `compare`; run it by hand on %s" % results_dir)
            return
        log("comparing baseline vs current -> %s.{json,md}" % out_prefix)
        completed = subprocess.run([java, "-jar", current_jar, "compare", "--baseline", baseline, "--current", current,
                                    "--out", out_prefix, "--lang", self.args.lang], check=False)
        if completed.returncode != 0:
            log("warning: compare exited with %d" % completed.returncode)
            return
        for extension in ("json", "md"):
            local = "%s.%s" % (out_prefix, extension)
            key = "%s/%s" % (self.s3_prefix, os.path.basename(local))
            self.s3.upload_file(local, self.bucket, key)
            log("  uploaded s3://%s/%s" % (self.bucket, key))

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
            state = self.instance_state()
            if state in ("shutting-down", "terminated"):
                break
            time.sleep(5)
        state = self.instance_state()
        log("  %s is %s" % (self.instance_id, state))
        if state not in ("shutting-down", "terminated"):
            fail("termination of %s was not confirmed; retained state at %s for `cleanup`"
                 % (self.instance_id, self.state_path))
        self.terminated = True
        self.clear_state()

    # ----------------------------------------------------------------------------------------- flows

    def execute(self) -> int:
        self.install_signal_handlers()
        try:
            self.preflight()
            if self.args.dry_run:
                log("dry run: nothing launched")
                return 0
            self.build()
            self.stage()
            self.launch()
            self.wait_for_ssm()
            self.bootstrap()
            self.smoke()
            self.start_run()
            failures = self.await_run()
            results = self.retrieve()
            self.compare(results)
            log("results: %s and %s/" % (self.outdir, self.s3_uri))
            return 1 if failures else 0
        finally:
            self.terminate()

    def resume(self, state: dict) -> int:
        self.instance_id = state["instance_id"]
        self.run_id = state["run_id"]
        self.arch = state["arch"]
        self.bucket = state["bucket"]
        self.args.prefix = state["prefix"]
        self.args.instance_type = state["instance_type"]
        self.args.suites = state["suites"]
        self.args.lang = state.get("lang", self.args.lang)
        self.args.keep_instance = self.args.keep_instance or state.get("keep_instance", False)
        self.launched_at = state.get("launched_at")
        if state.get("current_jar"):
            self.jars["current"] = state["current_jar"]
        if state.get("baseline"):
            self.jars["baseline"] = "baseline.jar"
        self.outdir = os.path.join(state["outdir"], self.run_id)
        self.install_signal_handlers()
        try:
            state_name = self.instance_state()
            if state_name != "running":
                fail("instance %s is %s; nothing to resume. Results, if any, are under %s"
                     % (self.instance_id, state_name, self.s3_uri))
            log("resuming run %s on %s (phase %s)" % (self.run_id, self.instance_id, state.get("phase")))
            if state.get("phase") != "running":
                fail("the run had not been started yet; terminate with `cleanup` and start over")
            failures = self.await_run()
            results = self.retrieve()
            self.compare(results)
            return 1 if failures else 0
        finally:
            self.terminate()

    def install_signal_handlers(self) -> None:
        def handler(signum, _frame):
            raise SystemExit("interrupted by signal %d" % signum)
        signal.signal(signal.SIGINT, handler)
        signal.signal(signal.SIGTERM, handler)


def parse_kv_line(output: str, marker: str) -> Dict[str, str]:
    for line in output.splitlines():
        if line.startswith(marker + " "):
            return dict(part.split("=", 1) for part in line.split()[1:] if "=" in part)
    return {}


def load_state(outdir: str) -> Optional[dict]:
    path = os.path.join(outdir, STATE_FILE)
    if not os.path.isfile(path):
        return None
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


def setup_infra(args: argparse.Namespace) -> int:
    """Creates the bucket, role and instance profile the runs need. Idempotent."""
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
        {"Effect": "Allow", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject"],
         "Resource": "arn:aws:s3:::%s/*" % bucket},
        {"Effect": "Allow", "Action": ["s3:ListBucket", "s3:GetBucketLocation"],
         "Resource": "arn:aws:s3:::%s" % bucket},
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

    if args.command == "resume":
        state = load_state(args.outdir)
        if not state:
            fail("no state file at %s; nothing to resume" % os.path.join(args.outdir, STATE_FILE))
        args.region = state.get("region", args.region)
        args.profile = state.get("profile", args.profile)
        args.poll_seconds = state.get("poll_seconds", args.poll_seconds)
        return MetalRun(args).resume(state)

    existing = load_state(args.outdir)
    if existing and not args.dry_run:
        fail("a run is already recorded in %s (instance %s). Use `resume` to finish it or `cleanup` to terminate it."
             % (os.path.join(args.outdir, STATE_FILE), existing.get("instance_id")))
    return MetalRun(args).execute()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
