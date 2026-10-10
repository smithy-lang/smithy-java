import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[2]


def load_module(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


metal = load_module("metal", "scripts/run-metal-benchmarks.py")
compare = load_module("compare", "scripts/compare-ocs.py")


class HostScriptTests(unittest.TestCase):
    def test_host_script_interleaves_sides_and_runs_the_jar_with_xbatch(self):
        args = metal.parse_args(["--samples", "3", "--e2e-args", '--mode https --notes-like "two words"',
                                 "--baseline-jar", "/tmp/b.jar"])
        script = metal.host_script(args, "run-1", "s3://b/stage", "s3://b/run", ["baseline", "current"])
        self.assertIn("for sample in $(seq 1 3); do", script)
        self.assertIn("for side in baseline current; do", script)
        self.assertIn("java -Xbatch -jar $WORK/jars/$side.jar --mode https --notes-like 'two words' "
                      "--instance-type m7i.metal-24xl", script)
        self.assertIn("echo performance >", script)
        self.assertIn("aws s3 cp --recursive --no-progress $RESULTS s3://b/run/results/", script)
        self.assertIn("echo $failures > $WORK/DONE", script)

    def test_host_script_counts_upload_failures_and_incomplete_uploads(self):
        args = metal.parse_args([])
        script = metal.host_script(args, "run-1", "s3://b/stage", "s3://b/run", ["current"])
        self.assertIn("if ! aws s3 cp --recursive --no-progress $RESULTS s3://b/run/results/", script)
        self.assertIn('uploaded=$(aws s3 ls --recursive s3://b/run/results/', script)
        self.assertIn('if [ "$uploaded" != "$expected" ]; then', script)
        self.assertEqual(script.count("failures=$((failures + 1))"), 3)
        self.assertNotIn("results/ | sed", script)

    def test_rejects_arguments_the_host_sets_itself(self):
        with self.assertRaises(SystemExit):
            metal.parse_args(["--e2e-args", "--output x.json"])


class MetalTests(unittest.TestCase):
    def make_run(self, directory):
        run = metal.MetalRun.__new__(metal.MetalRun)
        run.args = metal.parse_args(["--outdir", directory])
        run.state_path = os.path.join(directory, metal.STATE_FILE)
        Path(run.state_path).write_text("saved state")
        run.instance_id = "i-test"
        run.terminated = False
        run.ec2 = Mock()
        return run

    def test_unconfirmed_termination_keeps_cleanup_state(self):
        with tempfile.TemporaryDirectory() as directory:
            run = self.make_run(directory)
            run.instance_state = Mock(return_value="running")
            with patch.object(metal.time, "sleep"), self.assertRaises(SystemExit):
                run.terminate()
            self.assertTrue(Path(run.state_path).exists())
            self.assertFalse(run.terminated)

    def test_rejected_termination_fails_and_keeps_cleanup_state(self):
        with tempfile.TemporaryDirectory() as directory:
            run = self.make_run(directory)
            run.ec2.terminate_instances.side_effect = metal.ClientError(
                {"Error": {"Code": "UnauthorizedOperation", "Message": "denied"}}, "TerminateInstances")
            with self.assertRaises(SystemExit):
                run.terminate()
            self.assertTrue(Path(run.state_path).exists())

    def test_incomplete_results_retry_the_upload_then_keep_the_instance(self):
        with tempfile.TemporaryDirectory() as directory:
            run = self.make_run(directory)
            run.args = metal.parse_args(["--outdir", directory, "--samples", "3", "--baseline-jar", "/tmp/b.jar"])
            run.jars = {"baseline": "/tmp/b.jar", "current": "/tmp/c.jar"}
            run.arch = "x86_64"
            run.bucket = "bucket"
            run.run_id = "run-1"
            run.retrieve = Mock(side_effect=[("results", 4), ("results", 5)])
            run.ssm_run = Mock(return_value=("Success", ""))
            with self.assertRaises(SystemExit) as raised:
                run.retrieve_all()
            self.assertIn("retrieved 5 of 6 result files", str(raised.exception))
            self.assertEqual(run.ssm_run.call_count, 1)
            self.assertIn("aws s3 cp --recursive", run.ssm_run.call_args.args[0][0])
            self.assertTrue(run.args.keep_instance)

    def test_complete_results_need_no_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            run = self.make_run(directory)
            run.args = metal.parse_args(["--outdir", directory, "--samples", "2"])
            run.jars = {"current": "/tmp/c.jar"}
            run.retrieve = Mock(return_value=("results", 2))
            run.ssm_run = Mock()
            self.assertEqual(run.retrieve_all(), "results")
            run.ssm_run.assert_not_called()
            self.assertFalse(run.args.keep_instance)

    def test_confirmed_termination_clears_cleanup_state(self):
        with tempfile.TemporaryDirectory() as directory:
            run = self.make_run(directory)
            run.instance_state = Mock(return_value="shutting-down")
            run.terminate()
            self.assertFalse(Path(run.state_path).exists())
            self.assertTrue(run.terminated)


def run_file(values, commit="aaaaaaa", mode="stub", fingerprints=None, under_warmed=False, xbatch=True):
    fingerprints = fingerprints or {}
    return {
        "schema": compare.SCHEMA,
        "metadata": {
            "lang": "Java", "sdk": "smithy-java", "sdk_version": "1.7.0", "client": "sync",
            "metric": "ops_per_cpu_second", "measurement": "getProcessCpuTime",
            "stop_condition": "min 50000 iterations OR 5 seconds CPU time (first met wins)",
            "min_iterations": 50000, "check_interval": 100, "warmup_policy": "auto",
            "warmup_policy_detail": "auto detail", "background_jit_compilation_disabled": xbatch,
            "mode": mode, "transport": "stub transport", "http_mock": "mock", "region": "us-east-1",
            "instance": "m7i.metal-24xl", "commit": commit, "branch": "main",
            "run_started_utc": "2026-10-10T01:02:03Z",
            "environment": {"java": {"version": "25.0.4"}, "os_label": "amd64-linux Amazon Linux 2023",
                            "cpu": "Xeon", "available_processors": 96},
        },
        "benchmarks": [
            {"id": benchmark_id, "protocol": "RestXml" if benchmark_id.startswith("restXml") else "AwsJson10",
             "ops_per_cpu_sec": value, "workload_fingerprint": fingerprints.get(benchmark_id, "fp-" + benchmark_id),
             "warmup": {"iterations": 20000 + len(benchmark_id)}, "under_warmed": under_warmed, "cpu_wall_ratio": 1.01}
            for benchmark_id, value in values.items()],
        "summary": {"cpu_wall_ratio": {"mean": 1.01}},
    }


class CompareTests(unittest.TestCase):
    def write(self, directory, name, content):
        path = os.path.join(directory, name)
        with open(path, "w", encoding="utf-8") as handle:
            json.dump(content, handle)
        return path

    def test_medians_geomeans_and_the_ocs_schema(self):
        with tempfile.TemporaryDirectory() as directory:
            b1 = self.write(directory, "b1.json", run_file({"awsJson1_0_GetItemOutput_S": 10_000, "restXml_GetObject_S": 8_000}))
            b2 = self.write(directory, "b2.json", run_file({"awsJson1_0_GetItemOutput_S": 12_000, "restXml_GetObject_S": 8_000}))
            b3 = self.write(directory, "b3.json", run_file({"awsJson1_0_GetItemOutput_S": 11_000, "restXml_GetObject_S": 9_000}))
            c1 = self.write(directory, "c1.json", run_file({"awsJson1_0_GetItemOutput_S": 22_000, "restXml_GetObject_S": 8_000}, commit="bbbbbbb"))
            out = os.path.join(directory, "sub", "m7imetal24xl_ocs_results")
            with patch("sys.stdout"):
                self.assertEqual(compare.main(["--baseline", b1, b2, b3, "--current", c1, "--out", out]), 0)
            with open(out + ".json", encoding="utf-8") as handle:
                result = json.load(handle)
            self.assertEqual(set(result), {"metadata", "benchmarks", "summary"})
            self.assertEqual(result["benchmarks"][0],
                             {"id": "awsJson1_0_GetItemOutput_S", "baseline": 11000, "current": 22000, "improvement_pct": 100.0})
            self.assertEqual(result["summary"]["protocols"]["AwsJson10"]["benchmark_count"], 1)
            self.assertEqual(list(result["summary"]["protocols"]), ["AwsJson10", "RestXml"])
            overall = result["summary"]["overall"]
            self.assertEqual(overall["baseline"], round((11000 * 8000) ** 0.5))
            self.assertEqual(overall["current"], round((22000 * 8000) ** 0.5))
            self.assertEqual(overall["aggregation"], "geometric_mean")
            meta = result["metadata"]
            self.assertEqual(meta["baseline_commit"], "aaaaaaa")
            self.assertEqual(meta["current_commit"], "bbbbbbb")
            self.assertEqual(meta["baseline_date"], "2026-10-10")
            self.assertEqual(meta["software"], [["Java", "25.0.4"], ["smithy-java", "1.7.0"]])
            self.assertEqual(meta["sides"]["baseline"]["samples"], 3)
            self.assertIn("Current has 1 sample(s) per benchmark; at least 3 are expected",
                          meta["comparability_warnings"])
            text = compare.report(result)
            self.assertIn("Overall Geometric Mean (2 benchmarks):", text)
            self.assertIn("CPU Time Reduction:", text)

    def test_different_modes_cases_and_fingerprints_are_compared_with_warnings(self):
        with tempfile.TemporaryDirectory() as directory:
            base = self.write(directory, "b.json", run_file({"restXml_GetObject_S": 1000, "restXml_GetObject_M": 500}))
            https = self.write(directory, "h.json", run_file({"restXml_GetObject_S": 100, "restXml_GetObject_L": 5},
                                                             mode="https", fingerprints={"restXml_GetObject_S": "changed"}))
            result = compare.compare(compare.Side("baseline", [base]), compare.Side("current", [https]), "smithy-java")
            self.assertEqual([b["id"] for b in result["benchmarks"]], ["restXml_GetObject_S"])
            self.assertEqual(result["summary"]["overall"]["improvement_pct"], -90.0)
            warnings = result["metadata"]["comparability_warnings"]
            self.assertIn("Different modes: baseline 'stub', current 'https'", warnings)
            self.assertIn("Benchmark sets differ: 1 only in baseline, 1 only in current; compared the 1 in common", warnings)
            self.assertTrue(any(w.startswith("Workload fingerprints differ for 1 benchmark(s)") for w in warnings))
            self.assertEqual(result["metadata"]["sides"]["baseline"]["mode"], "stub")
            self.assertEqual(result["metadata"]["sides"]["current"]["mode"], "https")
            self.assertIn("Mode:         stub (baseline) vs https (current)", compare.report(result))

    def test_only_no_common_benchmarks_is_an_error(self):
        with tempfile.TemporaryDirectory() as directory:
            base = self.write(directory, "b.json", run_file({"restXml_GetObject_S": 1000}))
            other = self.write(directory, "o.json", run_file({"restXml_GetObject_M": 1100}))
            with self.assertRaises(SystemExit) as raised:
                compare.compare(compare.Side("baseline", [base]), compare.Side("current", [other]), "smithy-java")
            self.assertIn("no benchmark ids in common", str(raised.exception))

    def test_accepts_a_minimal_run_file_from_another_harness(self):
        with tempfile.TemporaryDirectory() as directory:
            base = self.write(directory, "b.json", run_file({"awsJson1_0_GetItemOutput_S": 1000}))
            foreign = self.write(directory, "f.json", {"benchmarks": [{"id": "awsJson1_0_GetItemOutput_S", "ops_per_cpu_sec": 2000}]})
            result = compare.compare(compare.Side("baseline", [base]), compare.Side("current", [foreign]), "other-sdk")
            self.assertEqual(result["benchmarks"][0]["improvement_pct"], 100.0)
            self.assertEqual(result["summary"]["protocols"]["AwsJson10"]["benchmark_count"], 1)
            self.assertEqual(compare.protocol_of("awsJson1_0_GetItemOutput_S", None), "AwsJson10")
            self.assertEqual(compare.protocol_of("other_Op_S", None), "other")
            warnings = result["metadata"]["comparability_warnings"]
            self.assertTrue(any("is not a smithy-java/e2e-ops-cpusec/2 file" in w for w in warnings))
            self.assertIn("Different modes: baseline 'stub', current None", warnings)
            self.assertIn("CPU/Wall Ratio: baseline 1.010, current n/a", compare.report(result))

    def test_samples_on_one_side_from_different_builds_only_warn(self):
        with tempfile.TemporaryDirectory() as directory:
            first = self.write(directory, "1.json", run_file({"restXml_GetObject_S": 1000}, commit="a"))
            second = self.write(directory, "2.json", run_file({"restXml_GetObject_S": 1000}, commit="b"))
            side = compare.Side("baseline", [first, second])
            self.assertTrue(any("baseline samples differ in commit" in w for w in side.warnings))

    def test_warns_about_missing_xbatch_and_under_warmed_samples(self):
        with tempfile.TemporaryDirectory() as directory:
            base = self.write(directory, "b.json", run_file({"restXml_GetObject_S": 1000}, xbatch=False))
            current = self.write(directory, "c.json", run_file({"restXml_GetObject_S": 1100}, under_warmed=True))
            result = compare.compare(compare.Side("baseline", [base]), compare.Side("current", [current]), "smithy-java")
            warnings = result["metadata"]["comparability_warnings"]
            self.assertIn("Baseline was measured without -Xbatch (or does not record it)", warnings)
            self.assertIn("1 under-warmed current sample(s)", warnings)


if __name__ == "__main__":
    unittest.main()
