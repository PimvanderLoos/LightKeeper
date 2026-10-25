"""Explicit historical-tag exceptions and a build-only Maven documentation overlay.

The listed tags predate versioned publishing and contain SNAPSHOT POM versions.
New releases must use matching stable Maven versions; this is not a general bypass.
"""

import argparse
import copy
import hashlib
from pathlib import Path
import xml.etree.ElementTree as ET

LEGACY = {
    "v1.0.0": ("0dfe7e14adc2a83c7fd6a830b728a2ef29094761", "1.0.0-SNAPSHOT"),
    "v1.1.0": ("d2eb30a3512b495d79effd4c5d3ccd631592d16b", "1.1.0-SNAPSHOT"),
    "v1.2.0": ("5f3a74e3f4b8bbff0b2ae62b0b320851aab405bb", "1.2.0-SNAPSHOT"),
}


def validate_legacy(tag, commit, source_version):
    """Require an exact match to the recorded historical source and Maven version."""
    if LEGACY.get(tag) != (commit, source_version):
        raise ValueError("Historical import does not match the tag/SHA/source-version allowlist")


def overlay(source, trusted_pom, tag):
    """Add only the trusted docs profile to an isolated historical checkout's POM."""
    from javadoc import git
    pom = source / "pom.xml"
    tree = ET.parse(pom)
    root = tree.getroot()
    validate_legacy(tag, git("rev-parse", "HEAD", repo=source), root.findtext("{*}version"))
    if git("rev-parse", f"refs/tags/{tag}^{{commit}}", repo=source) != LEGACY[tag][0]:
        raise ValueError("Historical tag moved from its recorded commit")
    trusted = ET.parse(trusted_pom).getroot()
    ns = "{http://maven.apache.org/POM/4.0.0}"
    profile = next(p for p in trusted.findall(f"{ns}profiles/{ns}profile") if p.findtext(f"{ns}id") == "docs")
    profiles = root.find(f"{ns}profiles")
    if profiles is None:
        profiles = ET.SubElement(root, f"{ns}profiles")
    if any(p.findtext(f"{ns}id") == "docs" for p in profiles):
        raise ValueError("Historical checkout already contains a docs profile")
    profiles.append(copy.deepcopy(profile))
    properties = root.find(f"{ns}properties")
    version = properties.find(f"{ns}version.maven-javadoc-plugin")
    if version is None:
        version = ET.SubElement(properties, f"{ns}version.maven-javadoc-plugin")
    version.text = trusted.findtext(f"{ns}properties/{ns}version.maven-javadoc-plugin")
    ET.register_namespace("", ns[1:-1])
    tree.write(pom, encoding="utf-8", xml_declaration=True)
    return hashlib.sha256(ET.tostring(profile)).hexdigest()


def main():
    """Validate and overlay one allowlisted historical checkout, without changing its version."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--trusted-pom", type=Path, default=Path("pom.xml"))
    parser.add_argument("--tag", choices=sorted(LEGACY), required=True)
    args = parser.parse_args()
    print(f"Historical docs profile SHA256: {overlay(args.source, args.trusted_pom, args.tag)}")


if __name__ == "__main__":
    main()
