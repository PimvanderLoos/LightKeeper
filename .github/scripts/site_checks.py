"""Validate static-site links and compile the Java examples embedded in its pages."""

import argparse
from html.parser import HTMLParser
import os
from pathlib import Path
import re
import subprocess
from urllib.parse import unquote, urlsplit
import xml.etree.ElementTree as ET


class Page(HTMLParser):
    """Collect links, anchors, and explicitly marked executable documentation snippets."""

    def __init__(self, source):
        """Parse HTML source while preserving decoded code-block text."""
        super().__init__(convert_charrefs=True)
        self.links = []
        self.properties = set(re.findall(r"-D(lightkeeper\.[\w.]+)", source))
        self.anchors = set()
        self.snippets = []
        self.snippet = None
        self.feed(source)

    def handle_starttag(self, tag, attrs):
        """Record resources and start an optional typed snippet."""
        attrs = dict(attrs)
        self.links.extend(attrs[key] for key in ("href", "src") if attrs.get(key))
        if attrs.get("id"):
            self.anchors.add(attrs["id"])
        if tag == "code":
            for kind in ("data-java-class", "data-maven-plugin"):
                if kind in attrs:
                    self.snippet = [kind, attrs[kind], ""]

    def handle_data(self, data):
        """Append decoded snippet content."""
        if self.snippet is not None:
            self.snippet[2] += data

    def handle_endtag(self, tag):
        """Finish a marked code snippet."""
        if tag == "code" and self.snippet is not None:
            self.snippets.append(tuple(self.snippet))
            self.snippet = None


def pages(site):
    """Parse all HTML pages in a site tree once for link validation."""
    return {path.resolve(): Page(path.read_text(encoding="utf-8")) for path in site.rglob("*.html")}


def check_links(site, source_only=False):
    """Reject missing local resources, broken fragments, and paths escaping the site."""
    root = site.resolve()
    parsed = pages(site)
    count = 0
    for path, page in parsed.items():
        for link in page.links:
            url = urlsplit(link)
            if url.scheme or url.netloc:
                continue
            target = ((root / unquote(url.path).lstrip("/")) if url.path.startswith("/")
                      else (path.parent / unquote(url.path) if url.path else path)).resolve()
            if not target.is_relative_to(root):
                raise ValueError(f"Link escapes website: {path}: {link}")
            if source_only and url.path in ("javadoc/", "/javadoc/"):
                continue
            if target.is_dir():
                target /= "index.html"
            if not target.is_file():
                raise ValueError(f"Missing local resource: {path}: {link}")
            if url.fragment and target in parsed and unquote(url.fragment) not in parsed[target].anchors:
                raise ValueError(f"Missing HTML anchor: {path}: {link}")
            count += 1
    print(f"Validated {count} local links/resources across {len(parsed)} HTML pages")
    return count


def compile_examples(site, classpath, output, plugin_descriptor):
    """Compile the site's actual Java snippets and validate documented Mojo configuration."""
    output.mkdir(parents=True, exist_ok=True)
    sources = []
    descriptor = ET.parse(plugin_descriptor).getroot()
    mojos = {mojo.findtext("goal"): {p.findtext("name") for p in mojo.findall("parameters/parameter")}
             for mojo in descriptor.findall("mojos/mojo")}
    properties = set(re.findall(r"\$\{(lightkeeper\.[\w.]+)\}", ET.tostring(descriptor, encoding="unicode")))
    plugin_count = 0
    for page in pages(site).values():
        if page.properties - properties:
            raise ValueError(f"Unknown documented Maven properties: {sorted(page.properties - properties)}")
        for kind, name, text in page.snippets:
            if kind == "data-java-class":
                if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", name):
                    raise ValueError(f"Invalid Java example name: {name}")
                path = output / f"{name}.java"
                if path in sources:
                    raise ValueError(f"Duplicate Java example: {name}")
                path.write_text(text, encoding="utf-8")
                sources.append(path)
            else:
                plugin_count += 1
                plugin = ET.fromstring(text)
                for execution in plugin.findall("executions/execution"):
                    for goal in execution.findall("goals/goal"):
                        if goal.text not in mojos:
                            raise ValueError(f"Unknown documented Maven goal: {goal.text}")
                        for config in (plugin.find("configuration"), execution.find("configuration")):
                            if config is not None:
                                unknown = {child.tag for child in config} - mojos[goal.text]
                                if unknown:
                                    raise ValueError(f"Unknown configuration for {goal.text}: {sorted(unknown)}")
    if not sources or not plugin_count:
        raise ValueError("Website must include executable Java and Maven examples")
    javac = str(Path(os.environ["JAVA_HOME"]) / "bin/javac") if os.environ.get("JAVA_HOME") else "javac"
    subprocess.run([javac, "--release", "21", "-proc:none", "-cp", classpath,
                    "-d", str(output), *map(str, sources)], check=True)
    print(f"Compiled {len(sources)} Java examples; validated {plugin_count} Maven plugin example")


def main():
    """Check source links or compiled documentation examples from CI."""
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--site", type=Path, required=True)
    parser.add_argument("--source-only", action="store_true")
    parser.add_argument("--classpath-file", type=Path)
    parser.add_argument("--plugin-descriptor", type=Path)
    args = parser.parse_args()
    check_links(args.site, args.source_only)
    if args.classpath_file:
        if not args.plugin_descriptor:
            parser.error("--plugin-descriptor is required with --classpath-file")
        classpath = str(args.classpath_file.parent / "classes") + os.pathsep + args.classpath_file.read_text().strip()
        compile_examples(args.site, classpath,
                         Path("target/site-examples"), args.plugin_descriptor)


if __name__ == "__main__":
    main()
