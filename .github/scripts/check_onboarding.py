"""Keep the complete first-test documentation identical to its executable consumer source."""

import argparse
from pathlib import Path
import re

from site_checks import pages


def check(root, site=None):
    """Reject drift between the canonical tested example and the copyable documentation."""
    source = (root / "examples/single-module/src/test/java/example/GettingStartedIT.java").read_text().strip()
    readme = (root / "README.md").read_text()
    match = re.search(r"<!-- lightkeeper:first-test -->\s*```java\n(.*?)\n```", readme, re.DOTALL)
    if match is None or match[1].strip() != source:
        raise ValueError("README first test must match the executable GettingStartedIT.java completely")
    if site is not None:
        snippets = [text for page in pages(site).values() for kind, name, text in page.snippets
                    if kind == "data-java-class" and name == "GettingStartedIT"]
        if not snippets or any(text.strip() != source for text in snippets):
            raise ValueError("Website first test must match the executable GettingStartedIT.java completely")
    print("Validated canonical first test against README" + (" and website" if site else ""))


def main():
    """Validate the repository README and optionally a companion gh_pages checkout."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", type=Path)
    args = parser.parse_args()
    check(Path(__file__).resolve().parents[2], args.site)


if __name__ == "__main__":
    main()
