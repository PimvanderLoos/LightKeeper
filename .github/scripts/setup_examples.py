"""Build website setup presets from the executable Maven consumer examples."""

import argparse
from copy import deepcopy
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}
SDK_GROUP = "nl.pim16aap2.lightkeeper"


def read(relative):
    """Read an example POM, keeping its namespace for deterministic selection."""
    return ET.parse(ROOT / relative).getroot()


def element(parent, path):
    """Require a canonical configuration element rather than silently generating partial setup."""
    selected = parent.find(path, NS)
    if selected is None:
        raise ValueError(f"Canonical example is missing {path}")
    return deepcopy(selected)


def text(nodes):
    """Serialize fragments without a namespace prefix so they merge into an existing Maven POM."""
    result = []
    for node in nodes:
        node = deepcopy(node)
        for child in node.iter():
            child.tag = child.tag.rsplit("}", 1)[-1]
            child.tail = None
        ET.indent(node, space="    ")
        result.append(ET.tostring(node, encoding="unicode", short_empty_elements=False))
    return "\n\n".join(result)


def plugin(pom, artifact):
    """Select a tested build plugin by artifact identity."""
    return element(pom, f"m:build/m:plugins/m:plugin[m:artifactId='{artifact}']")


def build(plugins):
    """Wrap selected tested plugins in their POM merge location."""
    result = ET.Element("build")
    group = ET.SubElement(result, "plugins")
    group.extend(plugins)
    return result


def presets():
    """Return portable setup fragments and the exact executable first test."""
    single = read("examples/single-module/pom.xml")
    reactor = read("examples/reactor/pom.xml")
    e2e = read("examples/reactor/e2e/pom.xml")
    version = element(read("pom.xml"), "m:version").text
    if element(single, "m:properties/m:lightkeeper.version").text != version:
        raise ValueError("Consumer example and SDK versions differ")
    properties = ET.Element("properties")
    ET.SubElement(properties, "lightkeeper.version").text = version
    repositories = element(single, "m:repositories")
    for repository in list(repositories):
        if repository.findtext("m:id", namespaces=NS) != "eldonexus-snapshots":
            repositories.remove(repository)
    shared = [properties, repositories, element(single, "m:pluginRepositories")]
    dependency = element(single, "m:dependencies/m:dependency[m:artifactId='lightkeeper-junit-starter']")
    dependencies = ET.Element("dependencies")
    dependencies.append(dependency)
    runner = [plugin(single, "maven-surefire-plugin"), plugin(single, "maven-failsafe-plugin")]
    reactor_dependencies = element(e2e, "m:dependencies")
    producer = reactor_dependencies.find("m:dependency", NS)
    for name, token in [("groupId", "__PRODUCER_GROUP__"), ("artifactId", "__PRODUCER_ARTIFACT__"),
                        ("version", "__PRODUCER_VERSION__")]:
        producer.find("m:" + name, NS).text = token
    preparation = plugin(e2e, "lightkeeper-maven-plugin")
    selection = preparation.find("m:configuration/m:pluginUnderTest", NS)
    selection.find("m:artifactId", NS).text = "__PRODUCER_ARTIFACT__"
    ET.SubElement(selection, "groupId").text = "__PRODUCER_GROUP__"
    return {
        "version": version,
        "single": text(shared + [dependencies, build([plugin(single, "lightkeeper-maven-plugin")] + runner)]),
        "parent": text(shared + [build([plugin(reactor, "lightkeeper-maven-plugin")])]),
        "e2e": text([reactor_dependencies, build([preparation] + runner)]),
        "test": (ROOT / "examples/single-module/src/test/java/example/GettingStartedIT.java").read_text(),
    }


def serialized():
    """Stable JSON makes drift reviewable and permits offline browser use after loading the page."""
    return json.dumps(presets(), indent=2, ensure_ascii=False) + "\n"


def main():
    """Generate the gh_pages asset or compare it with current tested source examples."""
    parser = argparse.ArgumentParser(description=__doc__)
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--output", type=Path)
    action.add_argument("--check", type=Path)
    args = parser.parse_args()
    expected = serialized()
    if args.output:
        args.output.write_text(expected)
    elif args.check.read_text() != expected:
        raise SystemExit("Setup presets differ from executable examples; regenerate with setup_examples.py --output")
    else:
        print("Validated website setup presets against executable Maven examples")


if __name__ == "__main__":
    main()
