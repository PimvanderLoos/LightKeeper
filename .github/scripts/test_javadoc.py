"""Regression coverage for public URL ownership, release selection, and safe replay."""

import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import javadoc as docs


class PublishingTest(unittest.TestCase):
    """Exercise real temporary trees and Git ancestry rather than mocked publication."""

    def setUp(self):
        """Create an isolated source history and a minimal existing guide website."""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.git("init", "-q")
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "--allow-empty", "-qm", "first")
        self.first = self.git("rev-parse", "HEAD")
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "--allow-empty", "-qm", "second")
        self.second = self.git("rev-parse", "HEAD")
        self.published = self.root / "published"
        self.published.mkdir()
        (self.published / "index.html").write_text("<h1>Existing guide</h1>")
        (self.published / "CNAME").write_text("lightkeeper.pim16aap2.nl")
        (self.published / "assets").mkdir()
        (self.published / "assets/styles.css").write_text("body {}")
        master = self.published / "javadoc/master"
        master.mkdir(parents=True)
        (master / "index.html").write_text("Seeded master")
        (master / "element-list").write_text("test.api\n")
        docs.write_json(master / docs.METADATA,
                        dict(channel="master", version="1.2.0-SNAPSHOT", commit=self.first,
                             run_number=0, run_attempt=0, tag=None, checksum=docs.tree_digest(master)))
        self.count = 0

    def git(self, *args):
        """Execute Git against the fixture source repository."""
        return docs.git("-c", "commit.gpgsign=false", *args, repo=self.repo)

    def artifact(self, version="1.3.0", channel="release", commit=None, run=1, content="API", tag=None):
        """Write a complete incoming artifact with independently computed checksums."""
        self.count += 1
        artifact = self.root / f"artifact-{self.count}"
        api = artifact / "javadoc"
        api.mkdir(parents=True)
        (api / "index.html").write_text(content)
        (api / "element-list").write_text("test.api\n")
        info = dict(channel=channel, version=version, commit=commit or self.first,
                    run_number=run, run_attempt=1, tag=(tag or f"v{version}") if channel == "release" else None)
        info["checksum"] = docs.tree_digest(api)
        docs.write_json(api / docs.METADATA, info)
        return artifact

    def publish(self, artifact, preview=False):
        """Run assembly, retaining its output as the next publication's archive."""
        output = self.root / f"output-{self.count}"
        changed = docs.assemble(self.published, artifact, output, self.repo, preview)
        shutil.rmtree(self.published)
        shutil.move(output, self.published)
        return changed

    def test_publish_shouldReplaceOnlySelectedSeriesAndRemoveOldClasses(self):
        # setup
        self.publish(self.artifact("1.2.0"))
        self.publish(self.artifact("1.3.0"))
        old = self.published / "javadoc/release/1.3/Removed.html"
        old.write_text("removed class")
        target = self.published / "javadoc/release/1.3"
        info = json.loads((target / docs.METADATA).read_text())
        info["checksum"] = docs.tree_digest(target)
        docs.write_json(target / docs.METADATA, info)
        other = (self.published / "javadoc/release/1.2/index.html").read_bytes()
        # execute
        self.publish(self.artifact("1.3.1", content="new API"))
        # verify
        self.assertFalse(old.exists())
        self.assertEqual("new API", (target / "index.html").read_text())
        self.assertEqual(other, (self.published / "javadoc/release/1.2/index.html").read_bytes())
        self.assertEqual("<h1>Existing guide</h1>", (self.published / "index.html").read_text())

    def test_publish_shouldRejectDelayedPatchUsingNumericOrdering(self):
        # setup
        self.publish(self.artifact("1.3.10", content="new"))
        # execute
        changed = self.publish(self.artifact("1.3.9", content="old"))
        # verify
        self.assertFalse(changed)
        self.assertEqual("new", (self.published / "javadoc/release/1.3/index.html").read_text())

    def test_publish_shouldUseOneSelectorEntryPerSeries(self):
        # setup
        self.publish(self.artifact("1.9.0"))
        self.publish(self.artifact("1.10.0"))
        # execute
        self.publish(self.artifact("1.10.1"))
        # verify
        selector = (self.published / "javadoc/index.html").read_text()
        self.assertEqual(1, selector.count('href="release/1.10/"'))
        self.assertLess(selector.index('release/1.10/'), selector.index('release/1.9/'))
        self.assertIn("1.10 — 1.10.1 (latest stable)", selector)
        self.assertFalse((self.published / "javadoc/release/1.10.1").exists())

    def test_publish_shouldMakeIdenticalReleaseReplayANoOp(self):
        # setup
        self.publish(self.artifact())
        # execute
        changed = self.publish(self.artifact(run=2))
        # verify
        self.assertFalse(changed)

    def test_publish_shouldRejectConflictingPatchBeforeCreatingOutput(self):
        # setup
        self.publish(self.artifact())
        artifact = self.artifact(commit=self.second)
        output = self.root / "conflicting"
        # execute
        with self.assertRaisesRegex(ValueError, "cannot change"):
            docs.assemble(self.published, artifact, output, self.repo)
        # verify
        self.assertFalse(output.exists())

    def test_publish_shouldRejectChangedContentForSamePatch(self):
        # setup
        self.publish(self.artifact())
        artifact = self.artifact(content="changed")
        # execute
        with self.assertRaisesRegex(ValueError, "cannot change"):
            self.publish(artifact)
        # verify
        self.assertEqual("API", (self.published / "javadoc/release/1.3/index.html").read_text())

    def test_publish_shouldIgnoreAnOlderMasterEvenWithHigherRunNumber(self):
        # setup
        self.publish(self.artifact(channel="master", version="1.3.0-SNAPSHOT", commit=self.second, run=2, content="new"))
        # execute
        changed = self.publish(self.artifact(channel="master", version="1.3.0-SNAPSHOT", run=99, content="old"))
        # verify
        self.assertFalse(changed)
        self.assertEqual("new", (self.published / "javadoc/master/index.html").read_text())

    def test_publish_shouldRejectDivergentMaster(self):
        # setup
        self.publish(self.artifact(channel="master", commit=self.second))
        self.git("checkout", "-q", self.first)
        self.git("-c", "user.name=Test", "-c", "user.email=test@example.com", "commit", "--allow-empty", "-qm", "divergent")
        artifact = self.artifact(channel="master", commit=self.git("rev-parse", "HEAD"))
        # execute
        with self.assertRaisesRegex(ValueError, "diverged"):
            self.publish(artifact)
        # verify
        self.assertEqual(self.second, docs.read_docs(self.published / "javadoc/master")["commit"])

    def test_publish_shouldIgnoreEarlierRunOfSameMasterCommit(self):
        # setup
        self.publish(self.artifact(channel="master", run=3, content="new"))
        # execute
        changed = self.publish(self.artifact(channel="master", run=2, content="old"))
        # verify
        self.assertFalse(changed)

    def test_publish_shouldPreserveGuidesAndReleasesWhenUpdatingMaster(self):
        # setup
        self.publish(self.artifact())
        # execute
        self.publish(self.artifact(channel="master"))
        # verify
        self.assertEqual("<h1>Existing guide</h1>", (self.published / "index.html").read_text())
        self.assertEqual("lightkeeper.pim16aap2.nl", (self.published / "CNAME").read_text())
        self.assertTrue((self.published / "javadoc/release/1.3/index.html").is_file())

    def test_publish_shouldNeverReplaceHostedWebsiteWithArtifactPages(self):
        # setup
        (self.published / "README.md").write_text("Website contributor instructions")
        workflow = self.published / ".github/workflows/check-website.yml"
        workflow.parent.mkdir(parents=True)
        workflow.write_text("Website validation workflow")
        before = {path.relative_to(self.published): path.read_bytes()
                  for path in self.published.rglob("*")
                  if path.is_file() and path.relative_to(self.published).parts[0] != "javadoc"}
        artifact = self.artifact(channel="master")
        (artifact / "guides").mkdir()
        (artifact / "guides/index.html").write_text("Incorrect website from code branch")
        # execute
        self.publish(artifact)
        # verify
        after = {path.relative_to(self.published): path.read_bytes()
                 for path in self.published.rglob("*")
                 if path.is_file() and path.relative_to(self.published).parts[0] != "javadoc"}
        self.assertEqual(before, after)

    def test_publish_shouldRequireAnExistingHostedWebsite(self):
        # setup
        (self.published / "index.html").unlink()
        artifact = self.artifact(channel="master")
        output = self.root / "missing-website-output"
        # execute
        with self.assertRaisesRegex(ValueError, "Hosted website must exist"):
            docs.assemble(self.published, artifact, output, self.repo)
        # verify
        self.assertFalse(output.exists())

    def test_publish_shouldPreserveWebsiteConfigurationAndExcludeCheckoutGit(self):
        # setup
        (self.published / ".gitignore").write_text("Website ignore rules")
        (self.published / ".git").mkdir()
        (self.published / ".git/config").write_text("private git config")
        # execute
        self.publish(self.artifact(channel="master"))
        # verify
        self.assertEqual("Website ignore rules", (self.published / ".gitignore").read_text())
        self.assertFalse((self.published / ".git").exists())

    def test_publish_shouldPreserveLicenseAndAdditionalHostedGuidePages(self):
        # setup
        (self.published / "LICENSE").write_text("Original license")
        artifact = self.artifact(channel="master")
        (self.published / "new-guide.html").write_text("New guide")
        # execute
        self.publish(artifact)
        # verify
        self.assertEqual("Original license", (self.published / "LICENSE").read_text())
        self.assertEqual("New guide", (self.published / "new-guide.html").read_text())

    def test_publish_shouldPreserveConfiguredCustomDomain(self):
        # setup
        artifact = self.artifact(channel="master")
        (self.published / "CNAME").write_text("another.example")
        # execute
        self.publish(artifact)
        # verify
        self.assertEqual("another.example", (self.published / "CNAME").read_text())

    def test_publish_shouldAllowExplicitLocalPreview(self):
        # setup
        artifact = self.artifact(channel="preview")
        # execute
        changed = self.publish(artifact, preview=True)
        # verify
        self.assertTrue(changed)
        self.assertTrue((self.published / "javadoc/master/index.html").is_file())

    def test_publish_shouldRejectOutputInsideArchive(self):
        # setup
        artifact = self.artifact()
        output = self.published / "nested-output"
        # execute
        with self.assertRaisesRegex(ValueError, "outside its input"):
            docs.assemble(self.published, artifact, output, self.repo)
        # verify
        self.assertFalse(output.exists())

    def test_publish_shouldRejectIncompleteOrTamperedArtifact(self):
        # setup
        artifact = self.artifact()
        (artifact / "javadoc/index.html").write_text("tampered")
        # execute
        with self.assertRaisesRegex(ValueError, "checksum"):
            self.publish(artifact)
        # verify
        self.assertEqual("<h1>Existing guide</h1>", (self.published / "index.html").read_text())

    def test_publish_shouldRejectMissingElementList(self):
        # setup
        artifact = self.artifact()
        (artifact / "javadoc/element-list").unlink()
        # execute
        with self.assertRaisesRegex(ValueError, "element-list"):
            self.publish(artifact)
        # verify
        self.assertFalse((self.published / "javadoc/release").exists())

    def test_publish_shouldRejectSymlinks(self):
        # setup
        artifact = self.artifact()
        (artifact / "javadoc/escape").symlink_to(self.root)
        # execute
        with self.assertRaisesRegex(ValueError, "Symbolic links"):
            self.publish(artifact)
        # verify
        self.assertFalse((self.published / "javadoc/release").exists())

    def test_publish_shouldRejectPreviewUnlessExplicitlyRequested(self):
        # setup
        artifact = self.artifact(channel="preview")
        # execute
        with self.assertRaisesRegex(ValueError, "cannot be published"):
            self.publish(artifact)
        # verify
        self.assertEqual("Seeded master", (self.published / "javadoc/master/index.html").read_text())

    def test_publish_shouldAllowFirstReleaseWithoutTakingOwnershipOfGuides(self):
        # setup
        shutil.rmtree(self.published / "javadoc/master")
        artifact = self.artifact()
        # execute
        self.publish(artifact)
        # verify
        self.assertTrue((self.published / "javadoc/release/1.3/index.html").is_file())
        self.assertEqual("<h1>Existing guide</h1>", (self.published / "index.html").read_text())

    def test_validate_shouldRejectUnsafeVersionPaths(self):
        # setup
        versions = ("../escape", "1.3", "1.3.0-rc1", "01.3.0", "1.3.0/evil")
        # execute
        for version in versions:
            with self.subTest(version=version), self.assertRaises(ValueError):
                docs.release_version(version)
        # verify
        self.assertFalse((self.root / "escape").exists())

    def test_banner_shouldShowExactPatchAndAbsoluteNavigation(self):
        # setup
        artifact = self.artifact("1.3.2")
        # execute
        banner = docs.banner(docs.read_docs(artifact / "javadoc"))
        # verify
        self.assertIn("Release 1.3.2", banner)
        self.assertIn(f'href="{docs.SITE_URL}javadoc/"', banner)
        self.assertIn(self.first, banner)

    def test_prepare_shouldPublishOnlyMasterOrStableTags(self):
        # setup
        pom = self.repo / "pom.xml"
        pom.write_text("<project><version>1.3.0-SNAPSHOT</version></project>")
        # execute
        master = docs.prepare(pom, {"GITHUB_REF": "refs/heads/master", "GITHUB_EVENT_NAME": "push"}, self.repo)
        preview = docs.prepare(pom, {"GITHUB_REF": "refs/pull/1/merge", "GITHUB_EVENT_NAME": "pull_request"}, self.repo)
        # verify
        self.assertEqual("master", master["channel"])
        self.assertEqual("preview", preview["channel"])

    def test_prepare_shouldRejectTagAndSnapshotVersionMismatch(self):
        # setup
        pom = self.repo / "pom.xml"
        pom.write_text("<project><version>1.3.0-SNAPSHOT</version></project>")
        self.git("tag", "v1.3.0")
        # execute
        with self.assertRaisesRegex(ValueError, "must match a non-SNAPSHOT"):
            docs.prepare(pom, {"GITHUB_REF": "refs/tags/v1.3.0", "GITHUB_EVENT_NAME": "push"}, self.repo)
        # verify
        self.assertEqual("<project><version>1.3.0-SNAPSHOT</version></project>", pom.read_text())

    def test_prepare_shouldAcceptMatchingStableTag(self):
        # setup
        pom = self.repo / "pom.xml"
        pom.write_text("<project><version>1.3.2</version></project>")
        self.git("tag", "v1.3.2")
        # execute
        info = docs.prepare(pom, {"GITHUB_REF": "refs/tags/v1.3.2", "GITHUB_EVENT_NAME": "push"}, self.repo)
        # verify
        self.assertEqual("release", info["channel"])
        self.assertEqual("v1.3.2", info["tag"])
        self.assertEqual(self.second, info["commit"])

    def test_prepare_shouldRejectTagPointingToDifferentSource(self):
        # setup
        pom = self.repo / "pom.xml"
        pom.write_text("<project><version>1.3.2</version></project>")
        self.git("tag", "v1.3.2", self.first)
        # execute
        with self.assertRaisesRegex(ValueError, "does not identify"):
            docs.prepare(pom, {"GITHUB_REF": "refs/tags/v1.3.2", "GITHUB_EVENT_NAME": "push"}, self.repo)
        # verify
        self.assertEqual(self.second, self.git("rev-parse", "HEAD"))

    def test_seal_shouldPackageOnlyApisAndRecordSelectedJdk(self):
        # setup
        artifact = self.artifact(channel="master")
        api = artifact / "javadoc"
        info = docs.read_docs(api)
        output = self.root / "sealed"
        def java_version(args, **kwargs):
            return SimpleNamespace(stderr="Selected JDK 21" if args[0] == "/selected-jdk/bin/java" else "PATH JDK 25")
        # execute
        with patch.dict(os.environ, {"JAVA_HOME": "/selected-jdk"}), \
             patch("javadoc.subprocess.run", side_effect=java_version), \
             patch("javadoc.subprocess.check_output", return_value="Maven 3.9.9"):
            docs.seal(api, info, output)
        # verify
        self.assertEqual("Selected JDK 21", docs.read_docs(output / "javadoc")["java"])
        self.assertEqual({"javadoc"}, {path.name for path in output.iterdir()})


if __name__ == "__main__":
    unittest.main()
