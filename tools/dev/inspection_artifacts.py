"""Shared worker artifact checks. Deployment inputs, never an uploaded-file parser."""
import hashlib
from pathlib import Path
import re
import stat
import zipfile


DEPENDENCIES = {
    ("tools.jackson.core", "jackson-core"): "3.1.7",
    ("tools.jackson.core", "jackson-databind"): "3.1.7",
    ("com.fasterxml.jackson.core", "jackson-annotations"): "2.21",
    **{("org.apache.pdfbox", name): "3.0.8" for name in ("pdfbox", "pdfbox-io", "fontbox")},
    ("org.apache.commons", "commons-compress"): "1.28.0",
    ("org.apache.commons", "commons-lang3"): "3.20.0",
    ("commons-io", "commons-io"): "2.20.0",
    ("commons-codec", "commons-codec"): "1.21.0",
    ("commons-logging", "commons-logging"): "1.3.6",
    **{("com.twelvemonkeys.imageio", name): "3.15.3" for name in ("imageio-webp", "imageio-core", "imageio-metadata")},
    **{("com.twelvemonkeys.common", name): "3.15.3" for name in ("common-lang", "common-io", "common-image")},
}


def regular(path):
    info = path.lstat()
    if not stat.S_ISREG(info.st_mode) or path.is_symlink() or getattr(info, "st_file_attributes", 0) & 0x400 or info.st_nlink != 1:
        raise ValueError("Expected a regular, unlinked deployment artifact")
    if info.st_size > 64 * 1024 * 1024:
        raise ValueError("Deployment artifact too large")


def directory(path):
    info = path.lstat()
    if not stat.S_ISDIR(info.st_mode) or path.is_symlink() or getattr(info, "st_file_attributes", 0) & 0x400:
        raise ValueError("Expected a real deployment directory")


def sha256(path):
    regular(path)
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def check_bundle(bundle):
    directory(bundle)
    directory(bundle / "lib")
    jars = list(bundle.glob("*-inspection.jar"))
    if len(jars) != 1 or not re.fullmatch(r"[A-Za-z0-9_.-]+-inspection\.jar", jars[0].name):
        raise ValueError("Expected one worker artifact in a fresh dedicated output")
    jar = jars[0]
    libraries = sorted((bundle / "lib").iterdir())
    if {p.name for p in bundle.iterdir()} - {jar.name, "lib", "verification.json"}:
        raise ValueError("Unexpected payload in worker bundle")
    if (bundle / "verification.json").exists() or (bundle / "verification.json").is_symlink():
        regular(bundle / "verification.json")
    regular(jar)
    fixed = {"com/sma/admin/file/FileFormat.class", "com/sma/admin/file/FileErrorCode.class",
             "com/sma/admin/common/BizException.class", "com/sma/admin/common/ErrorCode.class"}
    with zipfile.ZipFile(jar) as archive:
        names = archive.namelist()
        if len(names) != len(set(names)):
            raise ValueError("Duplicate worker ZIP entries")
        metadata = {"META-INF/MANIFEST.MF", "META-INF/maven/com.sma/springboot-modulith-admin/pom.xml",
                    "META-INF/maven/com.sma/springboot-modulith-admin/pom.properties"}
        for name in names:
            if name.endswith("/") or name in metadata:
                continue
            helper = re.fullmatch(r"com/sma/admin/file/internal/service/(FileContentProbe|FileNameRules)(\$[A-Za-z0-9_$]+)?\.class", name)
            inspection = re.fullmatch(r"com/sma/admin/file/internal/inspection/[A-Za-z0-9_$]+\.class", name)
            if name not in fixed and not helper and not inspection:
                raise ValueError("Unexpected worker payload")
        if "com/sma/admin/file/internal/inspection/ContentInspectionWorker.class" not in names:
            raise ValueError("Worker entry missing")
    coordinates = set()
    for library in libraries:
        regular(library)
        with zipfile.ZipFile(library) as archive:
            found = []
            for item in archive.infolist():
                if item.filename.startswith("META-INF/maven/") and item.filename.endswith("/pom.properties"):
                    if item.file_size > 8192:
                        raise ValueError("Library metadata too large")
                    properties = dict(re.findall(r"^([A-Za-z]+)=(.*)$", archive.read(item).decode("utf-8"), flags=re.M))
                    if all(key in properties for key in ("groupId", "artifactId", "version")):
                        found.append(properties)
            if len(found) != 1:
                raise ValueError("Ambiguous library provenance")
            item = found[0]
            key = (item["groupId"], item["artifactId"])
            if key in coordinates or DEPENDENCIES.get(key) != item["version"]:
                raise ValueError("Unexpected, duplicate or unreviewed library version")
            if library.name != item["artifactId"] + "-" + item["version"] + ".jar":
                raise ValueError("Library filename differs from metadata")
            coordinates.add(key)
    if coordinates != set(DEPENDENCIES):
        raise ValueError("Incomplete worker dependencies")
    return jar, libraries
