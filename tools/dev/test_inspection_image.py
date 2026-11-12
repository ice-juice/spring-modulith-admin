"""Local context/manifest tests. Synthetic image digests are NOT registry or container evidence."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

from inspection_artifacts import DEPENDENCIES, check_bundle

spec = importlib.util.spec_from_file_location("prepare_image", Path(__file__).with_name("prepare-inspection-image.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ImageContextTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="inspection-context-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.bundle = self.root / "bundle"
        (self.bundle / "lib").mkdir(parents=True)
        self.worker = self.bundle / "example-inspection.jar"
        with zipfile.ZipFile(self.worker, "w") as jar:
            jar.writestr("com/sma/admin/file/internal/inspection/ContentInspectionWorker.class", b"synthetic-not-executable")
        for (group, artifact), version in DEPENDENCIES.items():
            with zipfile.ZipFile(self.bundle / "lib" / f"{artifact}-{version}.jar", "w") as jar:
                jar.writestr(f"META-INF/maven/{group}/{artifact}/pom.properties", f"groupId={group}\nartifactId={artifact}\nversion={version}\n")
        self.pins = self.root / "pins.json"
        self.pin_values = {"javaImage": "docker.io/library/eclipse-temurin@sha256:" + "1" * 64,
                           "ffprobeImage": "docker.io/mwader/static-ffmpeg@sha256:" + "2" * 64}
        self.write(self.pins, self.pin_values)
        self.evidence_path = self.bundle / "verification.json"
        self.evidence = {"scope": module.SCOPE, "results": [{"name": name, "verdict": "REJECT" if name.startswith("denied-") else "PASS",
                         "code": "CONTENT_REJECTED" if name.startswith("denied-") else "NONE"} for name in sorted(module.REQUIRED_CASES)],
                         "artifacts": [{"name": str(p.relative_to(self.bundle)).replace("\\", "/"), "size": p.stat().st_size, "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in [self.worker] + sorted((self.bundle / "lib").iterdir())]}
        self.write(self.evidence_path, self.evidence)
        self.output = self.root / "context"

    def write(self, path, data):
        path.write_text(json.dumps(data), encoding="utf-8")

    def prepare(self):
        return module.prepare(self.bundle, self.pins, self.output)

    def test_minimal_copy_and_rendered_runtime_contract(self):
        result = self.prepare()
        self.assertEqual("prepared-not-built-or-isolation-tested", result["status"])
        self.assertEqual(18, len(result["files"]))
        self.assertEqual({"worker.jar", "lib", "Dockerfile", ".dockerignore", "context-manifest.json"}, {p.name for p in self.output.iterdir()})
        dockerfile = (self.output / "Dockerfile").read_text()
        self.assertNotIn("@@", dockerfile)
        self.assertIn("USER 65532:65532", dockerfile)
        self.assertIn('"--hold-output"', dockerfile)
        self.assertIn("CMD []", dockerfile)
        self.assertIn("COPY --chmod=0555 lib/", dockerfile)
        self.assertIn("Temurin-21.0.10+7", dockerfile)
        self.assertIn("ffprobe version 8\\.1\\.2", dockerfile)
        self.assertNotIn("COPY . ", dockerfile)
        self.assertNotIn("context-manifest", (self.output / ".dockerignore").read_text())
        self.assertEqual(self.worker.read_bytes(), (self.output / "worker.jar").read_bytes())

    def test_tags_shell_newlines_wrong_repository_and_unpinned_images_rejected(self):
        for invalid in ("eclipse-temurin:latest", "docker.io/library/eclipse-temurin:21", self.pin_values["javaImage"] + "\nRUN id",
                        "docker.io/other/image@sha256:" + "a" * 64, "docker.io/library/eclipse-temurin@sha256:" + "0" * 64):
            with self.subTest(invalid=invalid):
                self.write(self.pins, {**self.pin_values, "javaImage": invalid})
                with self.assertRaises(ValueError): self.prepare()
                self.assertFalse(self.output.exists())

    def test_stale_or_traversing_or_duplicate_artifact_evidence_rejected(self):
        for change in ("digest", "size", "path", "duplicate"):
            with self.subTest(change=change):
                evidence = json.loads(json.dumps(self.evidence))
                first = evidence["artifacts"][0]
                if change == "digest": first["sha256"] = "a" * 64
                if change == "size": first["size"] += 1
                if change == "path": first["name"] = "../secret.jar"
                if change == "duplicate": evidence["artifacts"][1] = dict(first)
                self.write(self.evidence_path, evidence)
                with self.assertRaises(ValueError): self.prepare()

    def test_missing_failed_duplicate_cases_or_scope_rejected(self):
        for change in ("scope", "missing", "failed", "duplicate"):
            with self.subTest(change=change):
                evidence = json.loads(json.dumps(self.evidence))
                if change == "scope": evidence["scope"] = "container-tested"
                if change == "missing": evidence["results"].pop()
                if change == "failed": evidence["results"][0]["verdict"] = "ERROR"
                if change == "duplicate": evidence["results"].append(evidence["results"][0])
                self.write(self.evidence_path, evidence)
                with self.assertRaises(ValueError): self.prepare()

    def test_extra_payloads_and_host_classes_rejected(self):
        extra = self.bundle / "application.yml"
        extra.write_text("synthetic-secret")
        with self.assertRaises(ValueError): self.prepare()
        extra.unlink()
        with zipfile.ZipFile(self.worker, "a") as jar:
            jar.writestr("com/sma/admin/identity/Subject.class", b"host-class")
        with self.assertRaises(ValueError): check_bundle(self.bundle)

    def test_helper_prefix_does_not_admit_unrelated_classes(self):
        with zipfile.ZipFile(self.worker, "a") as jar:
            jar.writestr("com/sma/admin/file/internal/service/FileNameRulesBackdoor.class", b"unrelated")
        with self.assertRaises(ValueError): check_bundle(self.bundle)

    def test_metadata_folder_does_not_hide_configuration(self):
        with zipfile.ZipFile(self.worker, "a") as jar:
            jar.writestr("META-INF/application.yml", b"synthetic-host-configuration")
        with self.assertRaises(ValueError): check_bundle(self.bundle)

    def test_current_bundle_with_synthetic_pins_only(self):
        actual = os.environ.get("SMA_INSPECTION_CONTEXT_TEST_BUNDLE")
        if not actual:
            self.skipTest("Explicit current artifact bundle required; no real artifact claim")
        result = module.prepare(Path(actual), self.pins, self.output)
        self.assertEqual(18, len(result["files"]))
        self.assertEqual("prepared-not-built-or-isolation-tested", result["status"])
        self.assertEqual(self.pin_values, result["runtimeImages"])
        self.assertIn("held-output-ack", {case["name"] for case in result["verification"]["results"]})

    def test_unreviewed_library_version_or_extra_library_rejected(self):
        extra = self.bundle / "lib/extra.jar"
        extra.write_bytes(b"not-a-jar")
        with self.assertRaises((ValueError, zipfile.BadZipFile)): self.prepare()
        extra.unlink()
        library = self.bundle / "lib/commons-io-2.20.0.jar"
        with zipfile.ZipFile(library, "w") as jar:
            jar.writestr("META-INF/maven/commons-io/commons-io/pom.properties", "groupId=commons-io\nartifactId=commons-io\nversion=0.0.1\n")
        with self.assertRaises(ValueError): check_bundle(self.bundle)

    def test_existing_context_is_never_overwritten(self):
        self.output.mkdir()
        sentinel = self.output / "owner.txt"
        sentinel.write_text("owned-by-another-run")
        with self.assertRaises(FileExistsError): self.prepare()
        self.assertEqual("owned-by-another-run", sentinel.read_text())

    def test_hardlinks_and_symlinks_are_rejected(self):
        hardlink = self.root / "linked-worker.jar"
        os.link(self.worker, hardlink)
        with self.assertRaises(ValueError): self.prepare()
        hardlink.unlink()
        # On Windows link creation may require a developer-mode capability. The lstat branch
        # is separately exercised by a reparse attribute below, without claiming a real junction.
        with patch.object(Path, "is_symlink", autospec=True, side_effect=lambda p: p == self.worker):
            with self.assertRaises(ValueError): self.prepare()

    def test_reparse_directory_rejected_before_resolve(self):
        original = Path.lstat
        def attributes(path):
            info = original(path)
            if path == self.bundle:
                return type("LinkedDirectory", (), {"st_mode": info.st_mode, "st_file_attributes": 0x400})()
            return info
        with patch.object(Path, "lstat", attributes):
            with self.assertRaises(ValueError): self.prepare()

    def test_copy_race_does_not_publish_dockerfile(self):
        def changed(original, copied, length): copied.write(b"changed-input")
        with patch.object(module.shutil, "copyfileobj", changed):
            with self.assertRaises(ValueError): self.prepare()
        self.assertFalse((self.output / "Dockerfile").exists())

    def test_duplicate_manifest_keys_rejected(self):
        self.pins.write_text('{"javaImage":"x","javaImage":"y","ffprobeImage":"z"}')
        with self.assertRaises(ValueError): self.prepare()


if __name__ == "__main__":
    unittest.main()
