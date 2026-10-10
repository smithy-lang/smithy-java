import importlib.util
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch


ROOT = Path(__file__).resolve().parents[2]


def load_module(name, relative):
    spec = importlib.util.spec_from_file_location(name, ROOT / relative)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


scheduler = load_module("scheduler", "benchmarks/e2e-benchmarks/fixture/e2e-scheduler.py")
metal = load_module("metal", "scripts/run-metal-benchmarks.py")


class SchedulerTests(unittest.TestCase):
    def test_client_uses_xbatch_and_preserves_quoted_arguments(self):
        args = scheduler.parse_args(["--e2e-args", '--notes "two words"'])
        with patch.object(scheduler.subprocess, "run", return_value=Mock(returncode=1, stdout="")) as run:
            scheduler.run_once(args, "case", "protocol", "stub", None, "missing.json")
        command = run.call_args.args[0]
        self.assertEqual(command[1:3], ["-Xbatch", "-jar"])
        self.assertEqual(command[-2:], ["--notes", "two words"])

    def test_stop_reaps_an_already_exited_server_and_is_idempotent(self):
        server = scheduler.FixtureServer.__new__(scheduler.FixtureServer)
        server.process = subprocess.Popen([sys.executable, "-c", "pass"], stdout=subprocess.PIPE)
        server.process.stdout.read()  # Read EOF without reaping the process.
        server.stop()
        self.assertEqual(server.process.returncode, 0)
        server.stop()

    def test_startup_accepts_jvm_warnings_before_readiness(self):
        with tempfile.TemporaryDirectory() as directory:
            launcher = Path(directory) / "fake-java"
            launcher.write_text("#!/bin/sh\necho 'JVM warning' >&2\n"
                                "echo 'Listening on http://127.0.0.1:1234 backend=test'\n"
                                "exec sleep 10\n")
            launcher.chmod(0o755)
            args = scheduler.parse_args(["--java", str(launcher)])
            server = scheduler.FixtureServer("http", {"server_args": []}, args)
            try:
                self.assertEqual(server.url, "http://127.0.0.1:1234")
            finally:
                server.stop()
            self.assertIsNotNone(server.process.returncode)


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

    def test_confirmed_termination_clears_cleanup_state(self):
        with tempfile.TemporaryDirectory() as directory:
            run = self.make_run(directory)
            run.instance_state = Mock(return_value="shutting-down")
            run.terminate()
            self.assertFalse(Path(run.state_path).exists())
            self.assertTrue(run.terminated)

    def test_resume_preserves_keep_instance(self):
        with tempfile.TemporaryDirectory() as directory:
            run = self.make_run(directory)
            run.jars = {}
            run.instance_state = Mock(return_value="running")
            run.install_signal_handlers = Mock()
            run.await_run = Mock(return_value=0)
            run.retrieve = Mock(return_value="results")
            run.compare = Mock()
            run.terminate = Mock()
            run.resume({
                "instance_id": "i-test", "run_id": "run", "arch": "arm64", "bucket": "bucket",
                "prefix": "prefix", "instance_type": "m7g.metal", "suites": ["e2e"],
                "outdir": directory, "phase": "running", "keep_instance": True,
            })
            self.assertTrue(run.args.keep_instance)


class ManifestTests(unittest.TestCase):
    def test_cli_flags_build_a_valid_manifest(self):
        args = scheduler.parse_args([
            "--jar", "/tmp/current.jar", "--baseline-jar", "/tmp/baseline.jar",
            "--benchmarks", "restXml_GetObject_L", "--modes", "stub,https", "--runs", "3"])
        manifest = scheduler.manifest_from_args(args)
        scheduler.validate_manifest(manifest)
        self.assertEqual(manifest["samples"], 3)
        self.assertEqual([s["label"] for s in manifest["sides"]], ["baseline", "current"])
        outs = [c["out"].rsplit("/", 1)[-1] for c in manifest["compare"]]
        self.assertIn("baseline_stub_vs_https", outs)
        self.assertIn("current_stub_vs_https", outs)
        self.assertIn("baseline_vs_current_https", outs)

    def test_metal_e2e_manifest_matches_scheduler_schema(self):
        run = metal.MetalRun.__new__(metal.MetalRun)
        run.args = metal.parse_args([
            "--instance-type", "m7i.metal-24xl", "--samples", "5", "--baseline-jar", "/tmp/baseline.jar"])
        run.jars = {"current": "/x/current.jar", "baseline": "/x/baseline.jar"}
        manifest = run.build_manifest()
        scheduler.validate_manifest(manifest)
        self.assertEqual(manifest["samples"], 5)
        self.assertTrue(manifest["imds"])
        self.assertIn("all", [c.get("group") for c in manifest["cases"]])
        self.assertTrue(any(c["out"].endswith("_ocs_results") for c in manifest["compare"]))

    def test_metal_fixture_manifest_matches_scheduler_schema(self):
        run = metal.MetalRun.__new__(metal.MetalRun)
        run.args = metal.parse_args([
            "--suite", "fixture", "--instance-type", "m7g.metal", "--samples", "4",
            "--baseline-jar", "/tmp/baseline.jar"])
        run.jars = {"current": "/x/current.jar", "baseline": "/x/baseline.jar"}
        manifest = run.build_manifest()
        scheduler.validate_manifest(manifest)
        self.assertTrue(all(c.get("id") for c in manifest["cases"]))
        self.assertTrue(any("stub_vs_https" in c["out"] for c in manifest["compare"]))


if __name__ == "__main__":
    unittest.main()
