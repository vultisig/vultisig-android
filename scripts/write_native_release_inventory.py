"""Write an inventory of the tagged APK and verify its crypto library inputs."""

import argparse
import hashlib
import json
import re
import subprocess
import zipfile
from pathlib import Path
from xml.etree import ElementTree


REPOSITORY = Path(__file__).resolve().parents[1]
MPC_ARCHIVE_NAMES = (
    "dilithium-release.aar",
    "dkls-release.aar",
    "goschnorr-release.aar",
    "mobile-tss-lib.aar",
)
APK_SOURCE_METADATA = "META-INF/version-control-info.textproto"
VERIFICATION_NAMESPACE = {"v": "https://schema.gradle.org/dependency-verification"}


def git(*arguments: str) -> str:
    return subprocess.check_output(["git", *arguments], cwd=REPOSITORY, text=True).strip()


def file_sha256(path: Path) -> str:
    with path.open("rb") as file:
        return hashlib.file_digest(file, "sha256").hexdigest()


def native_library_hashes(archive: zipfile.ZipFile, directory: str) -> dict[str, str]:
    hashes = {}
    for member in archive.infolist():
        if not re.fullmatch(rf"{directory}/[^/]+/[^/]+\.so", member.filename):
            continue
        _, path = member.filename.split("/", 1)
        if path in hashes:
            raise ValueError(f"Duplicate native library in {archive.filename}: {path}")
        with archive.open(member) as library:
            hashes[path] = hashlib.file_digest(library, "sha256").hexdigest()
    return hashes


def check_apk_source(apk: Path, commit: str) -> dict[str, str]:
    with zipfile.ZipFile(apk) as archive:
        metadata = archive.read(APK_SOURCE_METADATA).decode("utf-8")
        repositories = re.findall(r"repositories\s*\{([^}]*)\}", metadata, re.S)
        for repository in repositories:
            if 'local_root_path: "$PROJECT_DIR"' in repository:
                revision = re.search(r'revision:\s*"([0-9a-f]+)"', repository)
                if revision and revision.group(1) == commit:
                    return native_library_hashes(archive, "lib")
    raise ValueError(f"APK source metadata does not identify the release commit {commit}")


def record_input(
    path: Path, apk_libraries: dict[str, str], source: dict[str, str]
) -> dict:
    with zipfile.ZipFile(path) as archive:
        input_libraries = native_library_hashes(archive, "jni")
    if not input_libraries:
        raise ValueError(f"No native libraries in {path.name}")
    for library, digest in input_libraries.items():
        if apk_libraries.get(library) != digest:
            raise ValueError(f"Packaged {library} differs from {path.name}")
    return {
        "file": path.name,
        "sha256": file_sha256(path),
        "source": source,
        "nativeLibraries": input_libraries,
    }


def record_wallet_core(path: Path, apk_libraries: dict[str, str]) -> dict:
    match = re.fullmatch(r"wallet-core-([A-Za-z0-9.-]+)\.aar", path.name)
    if not match:
        raise ValueError("Expected a versioned Wallet Core AAR")
    version = match.group(1)
    verification = ElementTree.parse(REPOSITORY / "gradle/verification-metadata.xml")
    checksum = verification.find(
        f".//v:component[@group='com.trustwallet'][@name='wallet-core'][@version='{version}']"
        f"/v:artifact[@name='{path.name}']/v:sha256",
        VERIFICATION_NAMESPACE,
    )
    result = record_input(
        path,
        apk_libraries,
        {
            "coordinates": f"com.trustwallet:wallet-core:{version}",
            "registry": "https://maven.pkg.github.com/trustwallet/wallet-core",
            "provenance": "verified-published-artifact",
        },
    )
    if checksum is None or result["sha256"] != checksum.get("value"):
        raise ValueError(f"{path.name} does not match the Wallet Core verification metadata")
    return result


def write_inventory(apk: Path, wallet_core: Path, tag: str, output: Path) -> None:
    commit = git("rev-parse", "HEAD")
    if git("rev-parse", "--verify", f"refs/tags/{tag}^{{commit}}") != commit:
        raise ValueError(f"Tag {tag} does not identify the checkout")
    subprocess.run(["git", "diff", "--quiet", "HEAD", "--"], cwd=REPOSITORY, check=True)
    submodules = git("submodule", "status", "--recursive")
    if any(line.startswith(("-", "+", "U")) for line in submodules.splitlines()):
        raise ValueError("Release submodules must match the tagged source tree")

    metadata = json.loads((apk.parent / "output-metadata.json").read_text(encoding="utf-8"))
    apk_version = next(
        item["versionName"] for item in metadata["elements"] if item["outputFile"] == apk.name
    )
    if tag != f"v{apk_version}":
        raise ValueError(f"Tag {tag} does not match APK version {apk_version}")
    apk_libraries = check_apk_source(apk, commit)

    inputs = [record_wallet_core(wallet_core, apk_libraries)]
    for name in MPC_ARCHIVE_NAMES:
        source_path = f"app/libs/{name}"
        inputs.append(
            record_input(
                REPOSITORY / source_path,
                apk_libraries,
                {
                    "gitPath": source_path,
                    "gitBlob": git("rev-parse", f"HEAD:{source_path}"),
                    "provenance": "committed-binary",
                },
            )
        )

    inventory = {
        "schemaVersion": 1,
        "releaseTag": tag,
        "sourceCommit": commit,
        "submodules": {
            fields[1]: fields[0] for line in submodules.splitlines() if (fields := line.split())
        },
        "apk": {
            "file": apk.name,
            "sha256": file_sha256(apk),
            "version": apk_version,
            "nativeLibraries": apk_libraries,
        },
        "cryptoInputs": inputs,
    }
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(inventory, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--wallet-core", type=Path, required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    try:
        write_inventory(arguments.apk, arguments.wallet_core, arguments.tag, arguments.output)
    except (
        ElementTree.ParseError,
        KeyError,
        OSError,
        StopIteration,
        ValueError,
        subprocess.CalledProcessError,
        zipfile.BadZipFile,
    ) as error:
        parser.exit(1, f"Release inventory failed: {error}\n")


if __name__ == "__main__":
    main()
