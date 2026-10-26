"""Build provenance, version selection, and assembly for the documentation publisher.

Only the standard library is required. See README.md for the command-line workflow.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import html
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET

SITE_URL = "https://lightkeeper.pim16aap2.nl/"
REPOSITORY = "https://github.com/PimvanderLoos/LightKeeper"
METADATA = "javadoc-build.json"
VERSION = re.compile(r"(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)")
SHA = re.compile(r"[0-9a-f]{40}")


def validate_output(output, *inputs):
    """Require a new staging directory outside every input tree."""
    if output.exists():
        raise ValueError(f"Output already exists: {output}")
    for source in inputs:
        if source is not None and output.resolve().is_relative_to(source.resolve()):
            raise ValueError(f"Output must be outside its input tree: {source}")


def release_version(version):
    """Parse a stable version numerically; reject path components and prereleases."""
    match = VERSION.fullmatch(version)
    if not match:
        raise ValueError(f"Expected major.minor.patch, got {version!r}")
    return tuple(map(int, match.groups()))


def series(version):
    """Return the major/minor destination for an exact stable patch version."""
    return ".".join(map(str, release_version(version)[:2]))


def git(*args, repo=Path(".")):
    """Run Git without shell interpolation and return its stripped output."""
    return subprocess.check_output(["git", "-C", str(repo), *args], text=True).strip()


def validate_info(info):
    """Validate publication identity before using any metadata as a path or HTML."""
    if info.get("channel") not in ("master", "release", "preview"):
        raise ValueError("Invalid documentation channel")
    if not isinstance(info.get("commit"), str) or not SHA.fullmatch(info["commit"]):
        raise ValueError("Invalid source commit")
    if not isinstance(info.get("version"), str):
        raise ValueError("Missing Maven version")
    release_version(info["version"].removesuffix("-SNAPSHOT"))
    for key in ("run_number", "run_attempt"):
        if type(info.get(key)) is not int or info[key] < 0:
            raise ValueError(f"Invalid {key}")
    if info["channel"] == "release":
        if not isinstance(info.get("tag"), str):
            raise ValueError("Missing release tag")
        version = info["tag"].removeprefix("v")
        release_version(version)
        if info["tag"] != "v" + version:
            raise ValueError("Invalid release tag")
        if info["version"] != version:
            from legacy_docs import validate_legacy
            validate_legacy(info["tag"], info["commit"], info["version"])
    return info


def banner(info):
    """Produce doclet top text whose navigation works at every class-page depth."""
    validate_info(info)
    label = (f"Release {info['tag'][1:]}" if info["channel"] == "release"
             else ("Master — development build" if info["channel"] == "master" else "PR preview"))
    return (f'<div class="lightkeeper-version">{html.escape(label)} · '
            f'Maven {html.escape(info["version"])} · '
            f'<a href="{REPOSITORY}/commit/{info["commit"]}">{info["commit"][:12]}</a> · '
            f'<a href="{SITE_URL}javadoc/">API versions</a> · '
            f'<a href="{SITE_URL}">Current master guides</a></div>')


def prepare(pom, env=os.environ, repo=Path("."), legacy_tag=None):
    """Identify a tested build; only master pushes and stable tags may publish."""
    version = ET.parse(pom).getroot().findtext("{*}version")
    ref, event = env.get("GITHUB_REF", ""), env.get("GITHUB_EVENT_NAME", "")
    channel = "preview"
    tag = legacy_tag or (ref.removeprefix("refs/tags/") if ref.startswith("refs/tags/") else None)
    if legacy_tag:
        channel = "release"
    elif event in ("push", "workflow_dispatch") and ref == "refs/heads/master":
        channel = "master"
    elif event == "push" and tag:
        release_version(tag.removeprefix("v"))
        channel = "release"
    commit = git("rev-parse", "HEAD", repo=repo)
    if tag and channel == "release" and git("rev-parse", f"refs/tags/{tag}^{{commit}}", repo=repo) != commit:
        raise ValueError("Release tag does not identify the checked-out source")
    # Normal tag builds must never use the historical-import exception implicitly.
    if channel == "release" and version != tag[1:] and not legacy_tag:
        raise ValueError("Release tag must match a non-SNAPSHOT Maven version; use the historical importer for old tags")
    info = validate_info(dict(channel=channel, version=version, commit=commit, tag=tag,
                              run_number=int(env.get("GITHUB_RUN_NUMBER", "0")),
                              run_attempt=int(env.get("GITHUB_RUN_ATTEMPT", "0"))))
    info["run_id"] = env.get("GITHUB_RUN_ID", "local")
    info["built_at"] = datetime.now(timezone.utc).isoformat()
    if legacy_tag:
        info["build_overlay_sha256"] = hashlib.sha256(git("diff", "--", "pom.xml", repo=repo).encode()).hexdigest()
    return info


def tree_digest(root):
    """Hash names and bytes deterministically, excluding the provenance document."""
    if not root.is_dir() or root.is_symlink():
        raise ValueError(f"Expected a real documentation directory: {root}")
    digest = hashlib.sha256()
    for path in sorted(root.rglob("*")):
        if path.is_symlink():
            raise ValueError(f"Symbolic links are forbidden: {path}")
        if path.is_file() and path != root / METADATA:
            digest.update(path.relative_to(root).as_posix().encode() + b"\0")
            digest.update(hashlib.sha256(path.read_bytes()).digest())
    return digest.hexdigest()


def write_json(path, value):
    """Write readable publication metadata with a deterministic key order."""
    path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def read_docs(docs):
    """Validate a complete, checksummed API artifact before changing output."""
    for filename in ("index.html", "element-list", METADATA):
        if not (docs / filename).is_file():
            raise ValueError(f"Incomplete Javadocs: missing {filename}")
    info = validate_info(json.loads((docs / METADATA).read_text(encoding="utf-8")))
    if tree_digest(docs) != info.get("checksum"):
        raise ValueError("Javadoc checksum mismatch")
    return info


def seal(docs, info, artifact):
    """Package generated APIs only; website pages remain owned by gh_pages."""
    validate_output(artifact, docs)
    validate_info(info)
    for filename in ("index.html", "element-list"):
        if not (docs / filename).is_file():
            raise ValueError(f"Incomplete Javadocs: missing {filename}")
    info = dict(info, checksum=tree_digest(docs))
    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java") if os.environ.get("JAVA_HOME") else "java"
    info["java"] = subprocess.run([java, "-version"], capture_output=True, text=True, check=True).stderr.strip()
    info["maven"] = subprocess.check_output(["mvn", "--version"], text=True).strip()
    artifact.mkdir(parents=True)
    shutil.copytree(docs, artifact / "javadoc")
    write_json(artifact / "javadoc" / METADATA, info)


def should_update(incoming, current, repo):
    """Reject stale builds and conflicting release replays without relying on job order."""
    if current is None:
        return True
    if incoming["channel"] == "release":
        old, new = release_version(current["tag"][1:]), release_version(incoming["tag"][1:])
        if new != old:
            return new > old
        if (incoming["commit"], incoming["checksum"]) != (current["commit"], current["checksum"]):
            raise ValueError("A published patch cannot change its source commit or content")
        return False
    if incoming["commit"] != current["commit"]:
        result = subprocess.run(["git", "-C", str(repo), "merge-base", "--is-ancestor",
                                 current["commit"], incoming["commit"]], check=False)
        if result.returncode == 0:
            return True
        reverse = subprocess.run(["git", "-C", str(repo), "merge-base", "--is-ancestor",
                                  incoming["commit"], current["commit"]], check=False)
        if result.returncode == 1 and reverse.returncode == 0:
            return False
        raise ValueError("Master history diverged or source commits are unavailable")
    if (incoming["run_number"], incoming["run_attempt"]) <= (current["run_number"], current["run_attempt"]):
        return False
    return True


def render_selector(site):
    """Render one API selector entry per series, with exact patch provenance."""
    entries = []
    releases = site / "javadoc/release"
    if releases.exists():
        for directory in releases.iterdir():
            if directory.is_dir():
                info = read_docs(directory)
                if info["channel"] != "release" or directory.name != series(info["tag"][1:]):
                    raise ValueError(f"Invalid release-series directory: {directory}")
                entries.append((release_version(info["tag"][1:]), directory.name, info))
    entries.sort(reverse=True)
    links = []
    for index, (_, name, info) in enumerate(entries):
        label = f"{name} — {info['tag'][1:]}" + (" (latest stable)" if index == 0 else "")
        links.append(f'<li><a href="release/{name}/">{label}</a></li>')
    master = site / "javadoc/master"
    if master.exists():
        info = read_docs(master)
        links.append(f'<li><a href="master/">Master — development build ({html.escape(info["version"])})</a></li>')
    (site / "javadoc/index.html").write_text(
        '<!doctype html><html lang="en"><head><meta charset="utf-8">'
        '<meta name="viewport" content="width=device-width, initial-scale=1">'
        '<title>LightKeeper API versions</title><link rel="stylesheet" href="../assets/styles.css">'
        '</head><body><main><h1>API Reference</h1><p>Choose a release series or the current development build.</p>'
        '<ul>' + "".join(links) + '</ul><p><a href="../index.html">Current master guides</a></p>'
        '</main></body></html>\n', encoding="utf-8")


def assemble(published, artifact, output, repo=Path("."), preview=False):
    """Stage API updates over the hosted website without changing website-owned files."""
    validate_output(output, published, artifact)
    info = read_docs(artifact / "javadoc")
    if info["channel"] == "preview" and not preview:
        raise ValueError("PR preview artifacts cannot be published")
    name = "release/" + series(info["tag"][1:]) if info["channel"] == "release" else "master"
    target = Path("javadoc") / name
    current = read_docs(published / target) if (published / target).exists() else None
    update = should_update(info, current, repo)
    if not published.is_dir() or not (published / "index.html").is_file():
        raise ValueError("Hosted website must exist on gh_pages before publishing Javadocs")
    # Preserve every website-owned file; the publisher owns only javadoc/.
    for source in published.iterdir():
        if source.name == ".git":
            continue
        if source.is_symlink():
            raise ValueError(f"Symbolic links are forbidden: {source}")
        if source.is_dir():
            tree_digest(source)
    shutil.copytree(published, output, ignore=shutil.ignore_patterns(".git"))
    if update:
        destination = output / target
        if destination.exists():
            shutil.rmtree(destination)
        shutil.copytree(artifact / "javadoc", destination)
    render_selector(output)
    from site_checks import check_links
    check_links(output)
    if sum(path.stat().st_size for path in output.rglob("*") if path.is_file()) >= 1_000_000_000:
        raise ValueError("Assembled site exceeds the GitHub Pages 1 GB limit")
    return update


def main():
    """Expose the prepare, seal, and assemble stages to GitHub Actions and local previews."""
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    prepare_parser = commands.add_parser("prepare")
    prepare_parser.add_argument("--pom", type=Path, default=Path("pom.xml"))
    prepare_parser.add_argument("--output", type=Path, required=True)
    prepare_parser.add_argument("--legacy-tag")
    seal_parser = commands.add_parser("seal")
    seal_parser.add_argument("--docs", type=Path, required=True)
    seal_parser.add_argument("--info", type=Path, required=True)
    seal_parser.add_argument("--artifact", type=Path, required=True)
    assemble_parser = commands.add_parser("assemble")
    assemble_parser.add_argument("--published", type=Path, required=True)
    assemble_parser.add_argument("--artifact", type=Path, required=True)
    assemble_parser.add_argument("--output", type=Path, required=True)
    assemble_parser.add_argument("--preview", action="store_true")
    args = parser.parse_args()
    if args.command == "prepare":
        info = prepare(args.pom, legacy_tag=args.legacy_tag)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        write_json(args.output, info)
        if os.environ.get("GITHUB_OUTPUT"):
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as stream:
                stream.write(f"publish={str(info['channel'] != 'preview').lower()}\ntop={banner(info)}\n")
        print(banner(info))
    elif args.command == "seal":
        seal(args.docs, json.loads(args.info.read_text()), args.artifact)
    else:
        print(f"Documentation updated: {assemble(args.published, args.artifact, args.output, preview=args.preview)}")


if __name__ == "__main__":
    main()
