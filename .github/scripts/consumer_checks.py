#!/usr/bin/env python3
"""Exercise installed SDK artifacts from independent, external Maven consumers."""

import argparse
from concurrent.futures import ThreadPoolExecutor
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape


SOURCE = Path(__file__).resolve().parents[2]
SDK_GROUP = Path("nl/pim16aap2/lightkeeper")
VERSION = "1.2.0-SNAPSHOT"
METHOD = ("example.SourceFreshnessIT#packagedCommand_shouldReturnCurrentProductionRevision"
          "(nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework)")


def require(condition, message):
    """Fail with a bounded, actionable assertion instead of silently skipping a scenario."""
    if not condition:
        raise AssertionError(message)


class ConsumerChecks:
    """Run real Maven/JUnit processes; retain diagnostics outside the source checkout."""

    def __init__(self, output):
        self.output = output
        self.repository = Path.home() / ".m2/repository"
        self.java = str(Path(os.environ["JAVA_HOME"]) / "bin/java")
        self.settings = output / "settings.xml"
        self.settings.write_text(
            '<settings><mirrors><mirror><id>installed-lightkeeper</id>'
            '<mirrorOf>eldonexus-snapshots</mirrorOf>'
            f'<url>{escape(self.repository.as_uri())}</url>'
            '</mirror></mirrors></settings>')
        self.completed = []

    def copy(self, name, destination):
        """Copy committed examples without an enclosing LK reactor or its .mvn settings."""
        return Path(shutil.copytree(SOURCE / "examples" / name, self.output / destination))

    def run(self, command, directory, name, succeeds=True):
        """Bound a process and its children, retaining a separate log per invocation."""
        log = self.output / f"{name}.log"
        with log.open("w") as stream:
            process = subprocess.Popen(command, cwd=directory, stdout=stream,
                                       stderr=subprocess.STDOUT, start_new_session=True)
            try:
                code = process.wait(timeout=600)
            except BaseException:
                import signal
                os.killpg(process.pid, signal.SIGKILL)
                process.wait()
                raise
        text = log.read_text(errors="replace")
        require((code == 0) == succeeds,
                f"Unexpected exit {code}: {name}. See {log}\n{text[-5000:]}")
        return text

    def maven(self, directory, name, *arguments, succeeds=True):
        return self.run(["mvn", "--batch-mode", "--no-transfer-progress", "--settings",
                         str(self.settings), *arguments], directory, name, succeeds)

    def classpath(self, directory, reactor=False):
        """Resolve from Maven artifacts, never append SDK target/classes directories."""
        arguments = ["org.apache.maven.plugins:maven-dependency-plugin:3.11.0:build-classpath",
                     "-Dmdep.outputFile=target/test-classpath.txt", "-DincludeScope=test"]
        if reactor:
            arguments = ["-pl", ":e2e", "-am", *arguments]
        self.maven(directory, directory.name + "-classpath", *arguments)
        module = directory / "e2e" if reactor else directory
        return str(module / "target/test-classes") + os.pathsep + (
            module / "target/test-classpath.txt").read_text().strip()

    def native(self, directory, cp, name, expected, selectors, count, succeeds=True, jvm=()):
        return self.run([self.java, f"-Dexpected.version={expected}",
                         f"-Dexpected.test.count={count}", *jvm, "-cp", cp,
                         "example.DirectJUnitLauncher", *selectors], directory, name, succeeds)

    def single_module(self, platform):
        root = self.copy("single-module", "single-" + platform)
        # Ordinary unit tests must remain independent of any server provisioning.
        log = self.maven(root, platform + "-unit", "-Dtest=OrdinaryUnitTest", "test")
        require("LK_BUILD:" not in log and not (root / ".lightkeeper/snapshots").exists()
                and not (root / "target/lightkeeper-server").exists(),
                "An unrelated unit test triggered LightKeeper preparation")
        log = self.maven(root, platform + "-verify", f"-Dlightkeeper.serverType={platform}",
                         f"-Dexpected.platform={platform.upper()}", "-Dit.test=SourceFreshnessIT", "verify")
        require("LK_BUILD:" not in log, "Failsafe unexpectedly invoked a nested producer build")
        cp = self.classpath(root)
        source = root / "src/main/java/example/ConsumerPlugin.java"
        source.write_text(source.read_text().replace("code-v1:", "code-v2:"))
        pom = root / "pom.xml"
        pom.write_text(pom.read_text().replace("<consumer.revision>v1", "<consumer.revision>v2"))
        (root / "src/main/resources/removed.txt").unlink()
        (root / "target/consumer-plugin-1.0.jar").unlink()
        shutil.rmtree(root / "target/lightkeeper-server")
        expected = "code-v2:resource-v2:removed"
        log = self.native(root, cp, platform + "-native-method", expected, [METHOD], 1,
                          jvm=[f"-Dexpected.platform={platform.upper()}"])
        require(log.count("LK_BUILD:") == 1, "Native method did not build exactly once")
        if platform == "paper":
            self.single_launch_cases(root, cp, expected)
        self.completed.append(f"{platform}: ordinary unit, normal Failsafe, native source/POM/resource freshness")
        return root, cp

    def single_launch_cases(self, root, cp, expected):
        selectors = ["example.SourceFreshnessIT", "example.FreshSessionIT", "example.IndependentSessionIT"]
        log = self.native(root, cp, "native-multiple-classes", expected, selectors, 4)
        require(log.count("LK_BUILD:") == 1, "Classes did not share one producer build")
        require("fresh-process-and-restart-retain-world=true" in log and
                "independent-class-clean-world=true" in log, "Session isolation/restart proof missing")
        with ThreadPoolExecutor(max_workers=2) as workers:
            results = list(workers.map(lambda index: self.native(
                root, cp, f"native-concurrent-{index}", expected, [METHOD], 1), range(2)))
        require(all(log.count("LK_BUILD:") == 1 for log in results),
                "Concurrent launches did not build independently")
        source = root / "src/main/java/example/ConsumerPlugin.java"
        original = source.read_text()
        try:
            source.write_text(original + "\ninvalid producer java source\n")
            log = self.native(root, cp, "native-failed-producer", expected,
                              ["example.SourceFreshnessIT", "example.IndependentSessionIT"], 2, False)
            require(log.count("LK_BUILD:") == 1 and "No previous runtime will be used" in log,
                    "A failed build was retried or an old runtime reused")
            match = re.search(r"log: (.*maven.log)", log)
            require(match is not None and "COMPILATION ERROR" in Path(match[1]).read_text(),
                    "Original producer compilation diagnostic missing")
        finally:
            source.write_text(original)
        # Recovery must release build locks rather than leave the checkout unusable.
        self.native(root, cp, "native-after-failure", expected, [METHOD], 1)
        self.completed.append("multiple classes, restart persistence, clean sessions, concurrent launches, failure recovery")
        self.active_artifact(root, cp, expected)
        self.intentional_failure(root, cp)
        self.selector_cases(root, cp, expected.replace("code-v2:", "code-v3:"))

    def active_artifact(self, root, cp, expected):
        probe = self.output / "active-session-probe"
        probe.mkdir()
        with ThreadPoolExecutor(max_workers=1) as workers:
            launch = workers.submit(self.native, root, cp, "native-active-session", expected,
                                    ["example.StableArtifactIT"], 1, True,
                                    [f"-Dconsumer.probeDirectory={probe}"])
            deadline = time.monotonic() + 120
            while not (probe / "session-ready").exists():
                if launch.done():
                    launch.result()
                    raise AssertionError("Active-session test did not publish readiness")
                require(time.monotonic() < deadline, "Timed out waiting for the active session")
                time.sleep(0.1)
            try:
                source = root / "src/main/java/example/ConsumerPlugin.java"
                source.write_text(source.read_text().replace("code-v2:", "code-v3:"))
                self.maven(root, "rebuild-during-active-session", "-DskipTests", "package")
            finally:
                (probe / "producer-rebuilt").write_text("continue")
            launch.result()
        self.native(root, cp, "native-after-active-session", expected.replace("code-v2:", "code-v3:"), [METHOD], 1)
        self.completed.append("active session retains private v2 JAR while real Maven builds v3; next launch sees v3")

    def intentional_failure(self, root, cp):
        descriptor = root / "src/main/resources/plugin.yml"
        original = descriptor.read_text()
        try:
            descriptor.write_text(original.replace("main: example.ConsumerPlugin", "main: example.IntentionallyAbsent"))
            self.native(root, cp, "native-intentional-load-failure", "unused",
                        ["example.IntentionalLoadFailureIT"], 1, jvm=["-Dconsumer.expectAbsent=true"])
        finally:
            descriptor.write_text(original)
        self.completed.append("valid descriptor with intentional load failure reaches a passing absence assertion")

    def skip_controls(self):
        for flag in ("skipITs", "maven.test.skip"):
            root = self.copy("single-module", "skip-" + flag)
            self.maven(root, "skip-" + flag, f"-D{flag}=true", "verify")
            require(not (root / "target/lightkeeper-server").exists(),
                    f"Skipped tests still provisioned a server: {flag}")
        root = self.copy("single-module", "no-matching-its")
        log = self.maven(root, "no-matching-its", "-Dit.test=DefinitelyMissingIT", "verify", succeeds=False)
        require("No tests matching pattern" in log, "Missing IT selection did not fail clearly")
        self.completed.append("skipITs/maven.test.skip avoid provisioning; no matching active IT fails clearly")

    def selector_cases(self, root, cp, expected):
        pom = root / "pom.xml"
        original = pom.read_text()
        namespace = "{http://maven.apache.org/POM/4.0.0}"
        ET.register_namespace("", namespace[1:-1])
        project = ET.fromstring(original)
        plugins = project.find(namespace + "build/" + namespace + "plugins")
        selected = next(plugin for plugin in plugins
                        if plugin.findtext(namespace + "artifactId") == "lightkeeper-maven-plugin")
        configuration = ET.SubElement(selected, namespace + "configuration")
        target = ET.SubElement(configuration, namespace + "pluginUnderTest")
        classifier = ET.SubElement(target, namespace + "classifier")
        classifier.text = "deploy"
        plugins.append(ET.fromstring('''<plugin xmlns="http://maven.apache.org/POM/4.0.0">
            <artifactId>maven-jar-plugin</artifactId><version>3.5.1</version><executions><execution>
            <id>deploy-artifact</id><phase>package</phase><goals><goal>jar</goal></goals>
            <configuration><classifier>deploy</classifier></configuration></execution></executions></plugin>'''))
        try:
            pom.write_text(ET.tostring(project, encoding="unicode"))
            self.native(root, cp, "native-attached-classifier", expected, [METHOD], 1)
            target.remove(classifier)
            path = ET.SubElement(target, namespace + "path")
            path.text = "${project.build.directory}/consumer-plugin-1.0-deploy.jar"
            pom.write_text(ET.tostring(project, encoding="unicode"))
            self.native(root, cp, "native-explicit-path", expected, [METHOD], 1)
            path.text = "${project.build.directory}/intentionally-missing.jar"
            pom.write_text(ET.tostring(project, encoding="unicode"))
            log = self.native(root, cp, "native-invalid-explicit-path", expected, [METHOD], 1, False)
            require("No previous runtime will be used" in log,
                    "Invalid explicit artifact silently fell back to a valid main artifact")
        finally:
            pom.write_text(original)
        self.completed.append("attached classifier and explicit JAR path work; invalid explicit path never falls back")

    def reactor(self, launcher_root):
        root = self.copy("reactor", "reactor")
        source = root / "core/src/main/java/example/internal/Revision.java"
        original = source.read_text()
        source.write_text(original.replace('return "v1";', 'return "stale-installed";'))
        self.maven(root, "reactor-install-stale", "-pl", ":packager", "-am", "-DskipTests", "install")
        source.write_text(original)
        log = self.maven(root, "reactor-current-verify", "-pl", ":e2e", "-am", "verify")
        require("LK_BUILD:" not in log, "Reactor Failsafe invoked another producer build")
        cp = self.classpath(root, True)
        # The launcher is test infrastructure, after the owning module's anchor on the classpath.
        cp += os.pathsep + str(launcher_root / "target/test-classes")
        source.write_text(original.replace('return "v1";', 'return "v2";'))
        log = self.native(root, cp, "reactor-native-v2", "v2", [METHOD], 1)
        require(log.count("LK_BUILD:") == 1, "Reactor source refresh did not build exactly once")
        import zipfile
        with zipfile.ZipFile(root / "packager/target/Actual-Assembly-Plugin.jar") as jar:
            require("shaded/example/internal/Revision.class" in jar.namelist(),
                    "Final Assembly artifact lost Shade relocation")
        self.completed.append("reactor: stale installed artifact defeated, current Shade/Assembly replacement tested")

    def isolated_resolution(self):
        root = self.copy("single-module", "isolated-consumer")
        repository = self.output / "isolated-repository"
        groups = [SDK_GROUP, Path("org/spigotmc/spigot-api")]
        for group in groups:
            base = self.repository / group
            require(base.is_dir(), f"Missing installed artifacts: {base}")
            for pom in base.rglob("*.pom"):
                if pom.parent.name not in (VERSION, "1.21.11-R0.2-SNAPSHOT"):
                    continue
                destination = repository / pom.parent.relative_to(self.repository)
                if destination.exists():
                    continue
                destination.mkdir(parents=True)
                for artifact in pom.parent.iterdir():
                    if artifact.suffix in (".jar", ".pom"):
                        shutil.copy2(artifact, destination / artifact.name)
                # A local installation is authoritative; do not request timestamped remote snapshots.
                artifact_id, version = pom.parent.parent.name, pom.parent.name
                group_id = '.'.join(pom.parent.parent.parent.relative_to(self.repository).parts)
                (destination / "maven-metadata-local.xml").write_text(
                    f'<metadata><groupId>{group_id}</groupId><artifactId>{artifact_id}</artifactId>'
                    f'<version>{version}</version><versioning><snapshot><localCopy>true</localCopy>'
                    '</snapshot><lastUpdated>20261114000000</lastUpdated></versioning></metadata>')
        settings = self.output / "isolated-settings.xml"
        settings.write_text('<settings><mirrors><mirror><id>read-only-warmed-artifacts</id>'
                            f'<mirrorOf>*</mirrorOf><url>{escape(self.repository.as_uri())}</url>'
                            '</mirror></mirrors></settings>')
        self.run(["mvn", "--batch-mode", "--no-transfer-progress", "--settings", str(settings),
                  f"-Dmaven.repo.local={repository}", "-Dtest=OrdinaryUnitTest", "test"],
                 root, "isolated-starter-resolution")
        self.completed.append("isolated local repository: starter, JUnit engine/API and Bukkit test types compile/run")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, help="New external directory for sources and logs")
    args = parser.parse_args()
    require(bool(os.environ.get("JAVA_HOME")), "Set JAVA_HOME to JDK 21 before running consumer checks")
    output = args.output.resolve() if args.output else Path(tempfile.mkdtemp(prefix="lk-consumers-"))
    if args.output:
        output.mkdir(parents=True, exist_ok=False)
    require(not output.is_relative_to(SOURCE), "Consumer checks must run outside the LK checkout")
    checks = ConsumerChecks(output)
    print(f"Consumer logs and reports: {output}", flush=True)
    root, _ = checks.single_module("paper")
    checks.single_module("spigot")
    checks.reactor(root)
    checks.skip_controls()
    checks.isolated_resolution()
    (output / "results.json").write_text(json.dumps(checks.completed, indent=2) + "\n")
    for result in checks.completed:
        print("PASS " + result, flush=True)


if __name__ == "__main__":
    main()
