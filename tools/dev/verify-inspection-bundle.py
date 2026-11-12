"""Verify the minimal Java worker artifact with its own classpath, never the Maven test classpath.
Uses synthetic inputs only. Does not assert OS/container/network isolation.
"""
import argparse
import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import zipfile
import zlib
from inspection_artifacts import check_bundle

parser = argparse.ArgumentParser()
parser.add_argument("--bundle", required=True, type=Path)
parser.add_argument("--java", required=True, type=Path)
parser.add_argument("--ffprobe", type=Path)
args = parser.parse_args()
bundle = args.bundle.resolve()
java = args.java.resolve(strict=True)
jar, libraries = check_bundle(bundle)
repo = Path(__file__).resolve().parents[2]
fixtures = repo / "backend/src/test/resources/file-inspection"

def png():
    def chunk(kind, content):
        data = kind + content
        return struct.pack(">I", len(content)) + data + struct.pack(">I", zlib.crc32(data))
    raw = b"".join(b"\0" + bytes([0, 100, 200, 255]) * 8 for _ in range(6))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", 8, 6, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")

def pdf():
    content = bytearray(b"%PDF-1.7\n")
    offsets = []
    for index, item in enumerate((b"<< /Type /Catalog /Pages 2 0 R >>", b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>", b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 200] >>"), 1):
        offsets.append(len(content))
        content.extend(str(index).encode() + b" 0 obj\n" + item + b"\nendobj\n")
    xref = len(content)
    content.extend(b"xref\n0 4\n0000000000 65535 f \n")
    for offset in offsets:
        content.extend(f"{offset:010d} 00000 n \n".encode())
    content.extend(f"trailer\n<< /Size 4 /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode())
    return bytes(content)

results = []
classpath = os.pathsep.join((str(jar), str(bundle / "lib/*")))
environment = {key: value for key, value in os.environ.items() if key.upper() in {"SYSTEMROOT", "WINDIR"}}
with tempfile.TemporaryDirectory(prefix="worker-smoke-", dir=bundle) as temporary:
    work = Path(temporary)
    environment.update(TMP=str(work), TEMP=str(work))

    def run(name, format_, content, verdict="PASS"):
        source = work / (name + ".blob")
        output = work / (name + "-output")
        output.mkdir()
        source.write_bytes(content)
        digest = hashlib.sha256(content).hexdigest()
        request = dict(schema=1, format=format_, size=len(content), checksum=digest, outputBudget=64*1024*1024)
        command = [str(java), "-Xmx256m", "-XX:MaxDirectMemorySize=16m", "-XX:ActiveProcessorCount=1", "-Djava.awt.headless=true"]
        if args.ffprobe:
            command.append("-Dsma.file.worker.ffprobe=" + str(args.ffprobe.resolve(strict=True)))
        command.extend(["-cp", classpath, "com.sma.admin.file.internal.inspection.ContentInspectionWorker", str(source), str(output)])
        result = subprocess.run(command, input=json.dumps(request).encode(), capture_output=True, timeout=40, cwd=work, env=environment)
        if result.returncode or len(result.stdout) > 16384 or len(result.stderr) > 16384:
            raise RuntimeError(f"Worker smoke failed: {name}; exit={result.returncode}")
        report = json.loads(result.stdout)
        if (report["sourceSize"], report["sourceChecksum"], report["format"], report["verdict"]) != (len(content), digest, format_, verdict):
            raise RuntimeError(f"Unexpected source-bound result: {name}: {report.get('code')}")
        if "normalized" in report and report["normalized"] is not None:
            derivative = report["normalized"]
            if derivative["name"] != "normalized.png":
                raise RuntimeError("Unexpected output path")
            data = (output / derivative["name"]).read_bytes()
            if len(data) != derivative["size"] or hashlib.sha256(data).hexdigest() != derivative["checksum"]:
                raise RuntimeError("Derivative digest mismatch")
        results.append(dict(name=name, verdict=report["verdict"], code=report["code"]))

    run("text", "TEXT", "纯Java制品验证".encode())
    run("json", "JSON", b'{"number":1}')
    run("csv", "CSV", b"name,value\nitem,12\n")
    run("png", "PNG", png())
    run("webp", "WEBP", (fixtures / "synthetic-alpha.webp").read_bytes())
    run("pdf", "PDF", pdf())
    import io
    for format_, folder, main, schema, element, content_type in (
            ("DOCX", "word", "document", "wordprocessingml", "document", "wordprocessingml.document"),
            ("XLSX", "xl", "workbook", "spreadsheetml", "workbook", "spreadsheetml.sheet"),
            ("PPTX", "ppt", "presentation", "presentationml", "presentation", "presentationml.presentation")):
        packed = io.BytesIO()
        with zipfile.ZipFile(packed, "w", zipfile.ZIP_DEFLATED) as archive:
            archive.writestr("[Content_Types].xml", "<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
                "<Default Extension='xml' ContentType='application/xml'/><Default Extension='png' ContentType='image/png'/>"
                f"<Override PartName='/{folder}/{main}.xml' ContentType='application/vnd.openxmlformats-officedocument.{content_type}.main+xml'/></Types>")
            archive.writestr("_rels/.rels", "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                f"<Relationship Id='main' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='{folder}/{main}.xml'/></Relationships>")
            archive.writestr(f"{folder}/{main}.xml", f"<{element} xmlns='http://schemas.openxmlformats.org/{schema}/2006/main'/>")
            archive.writestr(f"{folder}/media/image.png", png())
        run(format_.lower(), format_, packed.getvalue())
    packed = io.BytesIO()
    with zipfile.ZipFile(packed, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("notes.txt", b"notes")
        archive.writestr("image.png", png())
        archive.writestr("document.pdf", pdf())
    run("zip", "ZIP", packed.getvalue())
    packed = io.BytesIO()
    with zipfile.ZipFile(packed, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("code.php.txt", b"text")
    run("denied-leaf", "ZIP", packed.getvalue(), "REJECT")
    run("denied-json", "JSON", b'{"duplicate":1,"duplicate":2}', "REJECT")
    if args.ffprobe:
        run("video", "MP4", (fixtures / "synthetic-audio-video.mp4").read_bytes())

    # Hold a real, separately packaged JVM alive while the parent verifies its derivative, then explicitly ACK.
    source = work / "held-image.blob"
    output = work / "held-output"
    output.mkdir()
    content = png()
    source.write_bytes(content)
    digest = hashlib.sha256(content).hexdigest()
    request = dict(schema=1, format="PNG", size=len(content), checksum=digest, outputBudget=1024*1024)
    command = [str(java), "-Xmx256m", "-XX:ActiveProcessorCount=1", "-Djava.awt.headless=true", "-cp", classpath,
               "com.sma.admin.file.internal.inspection.ContentInspectionWorker", str(source), str(output), "--hold-output"]
    child = subprocess.Popen(command, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, cwd=work, env=environment)
    reader = concurrent.futures.ThreadPoolExecutor(max_workers=1)
    try:
        child.stdin.write(json.dumps(request).encode() + b"\n")
        child.stdin.flush()
        frame = reader.submit(child.stdout.readline, 16386).result(timeout=15)
        if not frame.endswith(b"\n") or len(frame) > 16385:
            raise RuntimeError("Invalid held-output report frame")
        report = json.loads(frame)
        if (report["schema"], report["engine"], report["sourceChecksum"], report["sourceSize"], report["format"], report["verdict"]) != (1, "sma-content/1", digest, len(content), "PNG", "PASS"):
            raise RuntimeError("Held-output source mismatch")
        derivative = report["normalized"]
        if derivative["name"] != "normalized.png":
            raise RuntimeError("Invalid held-output filename")
        data = (output / "normalized.png").read_bytes()
        if len(data) != derivative["size"] or hashlib.sha256(data).hexdigest() != derivative["checksum"] or child.poll() is not None:
            raise RuntimeError("Held output lost before parent verification")
        child.stdin.write(b"\x06")
        child.stdin.flush()
        rest, errors = child.communicate(timeout=5)
        if child.returncode != 0 or rest or errors:
            raise RuntimeError("Held-output completion failed")
        results.append(dict(name="held-output-ack", verdict="PASS", code="NONE"))
    finally:
        if child.poll() is None:
            child.kill()
            child.wait(timeout=5)
        reader.shutdown(wait=True)

manifest = {"scope": "standalone-artifact-smoke-not-container-isolation", "results": results,
            "artifacts": [{"name": str(path.relative_to(bundle)).replace("\\", "/"), "size": path.stat().st_size,
                           "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for path in [jar] + libraries]}
(bundle / "verification.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps({"cases": len(results), "libraries": len(libraries), "result": "PASS", "container_isolation": "not-tested"}))
