"""Prepare a minimal, digest-pinned Docker context. Does not build/run a container or contact a registry."""
import argparse
import json
from pathlib import Path
import re
import shutil

from inspection_artifacts import check_bundle, directory, regular, sha256

SCOPE = "standalone-artifact-smoke-not-container-isolation"
REQUIRED_CASES = {"text", "json", "csv", "png", "webp", "pdf", "docx", "xlsx", "pptx", "zip",
                  "denied-leaf", "denied-json", "held-output-ack"}
TEMPLATE = Path(__file__).resolve().parents[1] / "deploy/inspection-worker.Dockerfile"


def read_json(path):
    regular(path)
    if path.stat().st_size > 65536:
        raise ValueError("Deployment manifest too large")
    def unique(items):
        result = {}
        for key, value in items:
            if key in result:
                raise ValueError("Duplicate manifest field")
            result[key] = value
        return result
    return json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=unique)


def prepare(bundle, pins_path, output):
    # Validate lexical ancestors before resolving; resolve() alone would hide links/junctions.
    for source in (bundle.absolute(), pins_path.absolute(), output.absolute().parent):
        for ancestor in source.parents:
            directory(ancestor)
    jar, libraries = check_bundle(bundle)
    pins = read_json(pins_path)
    if not isinstance(pins, dict) or set(pins) != {"javaImage", "ffprobeImage"}:
        raise ValueError("Expected exact reviewed runtime image pins")
    for key, repository in (("javaImage", "docker.io/library/eclipse-temurin"), ("ffprobeImage", "docker.io/mwader/static-ffmpeg")):
        value = pins[key]
        if not isinstance(value, str) or not re.fullmatch(re.escape(repository) + r"@sha256:[0-9a-f]{64}", value) or value.endswith("0" * 64):
            raise ValueError("Runtime images require reviewed sha256 digests, never tags/placeholders")
    evidence = read_json(bundle / "verification.json")
    if evidence.get("scope") != SCOPE:
        raise ValueError("Standalone artifact verification is required")
    cases = evidence.get("results")
    if not isinstance(cases, list) or len(cases) > 32:
        raise ValueError("Invalid verification cases")
    seen = set()
    for case in cases:
        name = case.get("name")
        denied = name in {"denied-leaf", "denied-json"}
        if name not in REQUIRED_CASES | {"video"} or name in seen or case.get("verdict") != ("REJECT" if denied else "PASS") or case.get("code") != ("CONTENT_REJECTED" if denied else "NONE"):
            raise ValueError("Unexpected/failed verification result")
        seen.add(name)
    if not REQUIRED_CASES <= seen:
        raise ValueError("Incomplete standalone verification")
    expected = {str(p.relative_to(bundle)).replace("\\", "/"): {"name": str(p.relative_to(bundle)).replace("\\", "/"), "size": p.stat().st_size, "sha256": sha256(p)} for p in [jar] + libraries}
    artifacts = evidence.get("artifacts")
    if not isinstance(artifacts, list) or len(artifacts) != len(expected):
        raise ValueError("Incomplete verification artifact list")
    seen = set()
    for item in artifacts:
        name = item.get("name")
        if name in seen or name not in expected or item != expected[name]:
            raise ValueError("Stale or foreign artifact verification")
        seen.add(name)
    directory(output.parent)
    output.mkdir(exist_ok=False)  # Never overwrite or delete another build/context.
    (output / "lib").mkdir()
    for source in [jar] + libraries:
        relative = str(source.relative_to(bundle)).replace("\\", "/")
        destination = output / ("worker.jar" if source == jar else relative)
        with source.open("rb") as original, destination.open("xb") as copied:
            shutil.copyfileobj(original, copied, 65536)
        if sha256(destination) != expected[relative]["sha256"]:
            raise ValueError("Artifact changed during context preparation")
    template = TEMPLATE.read_text(encoding="utf-8")
    dockerfile = template.replace("@@JAVA_IMAGE@@", pins["javaImage"]).replace("@@FFPROBE_IMAGE@@", pins["ffprobeImage"])
    manifest = {"schema": 1, "status": "prepared-not-built-or-isolation-tested", "platform": "linux/amd64",
                "javaVersion": "21.0.10+7", "ffprobeVersion": "8.1.2", "runtimeImages": pins,
                "verification": evidence, "files": [{"name": str(p.relative_to(output)).replace("\\", "/"), "sha256": sha256(p)} for p in [output / "worker.jar"] + sorted((output / "lib").iterdir())]}
    (output / "context-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    (output / ".dockerignore").write_text("**\n!Dockerfile\n!worker.jar\n!lib/\n!lib/*.jar\n", encoding="utf-8")
    # Publish build instructions last. A partial copy has no usable Dockerfile.
    (output / "Dockerfile").write_text(dockerfile, encoding="utf-8")
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--bundle", required=True, type=Path)
    parser.add_argument("--pins", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    result = prepare(args.bundle, args.pins, args.output)
    print(json.dumps({"status": result["status"], "jars": len(result["files"]), "platform": result["platform"]}))
