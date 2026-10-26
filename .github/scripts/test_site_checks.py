"""Checks for broken website links and executable documentation examples."""

from pathlib import Path
import subprocess
import tempfile
import unittest

import site_checks


class SiteChecksTest(unittest.TestCase):
    """Validate real HTML trees and Java compilation in disposable directories."""

    def setUp(self):
        """Create an isolated source website and Mojo descriptor."""
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.descriptor = self.root / "plugin.xml"
        self.descriptor.write_text('<plugin><mojos><mojo><goal>prepare-server</goal><parameters>'
                                   '<parameter><name>serverType</name></parameter>'
                                   '</parameters></mojo></mojos></plugin>')

    def snippets(self, java="class Example {}", parameter="serverType"):
        """Write one Java snippet and one independently validated Maven snippet."""
        (self.root / "index.html").write_text(
            f'<code data-java-class="Example">{java}</code>'
            '<code data-maven-plugin="lightkeeper">&lt;plugin&gt;&lt;executions&gt;&lt;execution&gt;'
            '&lt;goals&gt;&lt;goal&gt;prepare-server&lt;/goal&gt;&lt;/goals&gt;'
            f'&lt;configuration&gt;&lt;{parameter}&gt;paper&lt;/{parameter}&gt;&lt;/configuration&gt;'
            '&lt;/execution&gt;&lt;/executions&gt;&lt;/plugin&gt;</code>')

    def test_checkLinks_shouldResolveDirectoriesAndAnchors(self):
        # setup
        (self.root / "index.html").write_text('<a href="guide/#setup">Guide</a>')
        (self.root / "guide").mkdir()
        (self.root / "guide/index.html").write_text('<h1 id="setup">Setup</h1>')
        # execute
        count = site_checks.check_links(self.root)
        # verify
        self.assertEqual(1, count)

    def test_checkLinks_shouldRejectMissingLocalResources(self):
        # setup
        (self.root / "index.html").write_text('<img src="missing.png">')
        # execute
        with self.assertRaisesRegex(ValueError, "Missing local resource"):
            site_checks.check_links(self.root)
        # verify
        self.assertFalse((self.root / "missing.png").exists())

    def test_checkLinks_shouldRejectBrokenAnchors(self):
        # setup
        (self.root / "index.html").write_text('<a href="#missing">Broken</a>')
        # execute
        with self.assertRaisesRegex(ValueError, "Missing HTML anchor"):
            site_checks.check_links(self.root)
        # verify
        self.assertNotIn("missing", site_checks.pages(self.root)[(self.root / "index.html").resolve()].anchors)

    def test_checkLinks_shouldAllowOnlyApiSelectorBeforeAssembly(self):
        # setup
        (self.root / "index.html").write_text('<a href="javadoc/">API</a>')
        # execute
        site_checks.check_links(self.root, source_only=True)
        # verify
        with self.assertRaisesRegex(ValueError, "Missing local resource"):
            site_checks.check_links(self.root)

    def test_checkLinks_shouldRejectEncodedPathTraversal(self):
        # setup
        (self.root / "index.html").write_text('<a href="%2e%2e/private">Escape</a>')
        # execute
        with self.assertRaisesRegex(ValueError, "escapes"):
            site_checks.check_links(self.root)
        # verify
        self.assertFalse((self.root / "private").exists())

    def test_compileExamples_shouldCompileActualHtmlAndValidateMojoParameters(self):
        # setup
        self.snippets()
        # execute
        site_checks.compile_examples(self.root, "", self.root / "compiled", self.descriptor)
        # verify
        self.assertTrue((self.root / "compiled/Example.class").is_file())

    def test_compileExamples_shouldRejectUnknownMavenConfiguration(self):
        # setup
        self.snippets(parameter="removedParameter")
        # execute
        with self.assertRaisesRegex(ValueError, "Unknown configuration"):
            site_checks.compile_examples(self.root, "", self.root / "compiled", self.descriptor)
        # verify
        self.assertFalse((self.root / "compiled/Example.class").exists())

    def test_compileExamples_shouldRejectStaleJavaApis(self):
        # setup
        self.snippets(java="class Example { void run() { removedApi(); } }")
        # execute
        with self.assertRaises(subprocess.CalledProcessError):
            site_checks.compile_examples(self.root, "", self.root / "compiled", self.descriptor)
        # verify
        self.assertFalse((self.root / "compiled/Example.class").exists())

    def test_compileExamples_shouldRejectRemovedCommandLineProperties(self):
        # setup
        self.snippets()
        page = self.root / "index.html"
        page.write_text(page.read_text() + '<code>mvn lightkeeper:prepare-server -Dlightkeeper.removed=true</code>')
        # execute
        with self.assertRaisesRegex(ValueError, "Unknown documented Maven properties"):
            site_checks.compile_examples(self.root, "", self.root / "compiled", self.descriptor)
        # verify
        self.assertFalse((self.root / "compiled/Example.class").exists())


if __name__ == "__main__":
    unittest.main()
