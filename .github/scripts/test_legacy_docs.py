"""Tests for the narrow historical SNAPSHOT-tag exception."""

from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

import legacy_docs


class LegacyDocsTest(unittest.TestCase):
    """Keep historical imports tied to their original source and version."""

    def test_validateLegacy_shouldAcceptRecordedIdentity(self):
        # setup
        commit, version = legacy_docs.LEGACY["v1.2.0"]
        # execute
        legacy_docs.validate_legacy("v1.2.0", commit, version)
        # verify
        self.assertEqual("1.2.0-SNAPSHOT", version)

    def test_validateLegacy_shouldRejectMovedOrUnknownTags(self):
        # setup
        cases = [("v1.2.0", "0" * 40, "1.2.0-SNAPSHOT"),
                 ("v1.3.0", "0" * 40, "1.3.0-SNAPSHOT")]
        # execute
        for args in cases:
            with self.subTest(args=args), self.assertRaises(ValueError):
                legacy_docs.validate_legacy(*args)
        # verify
        self.assertNotIn("v1.3.0", legacy_docs.LEGACY)

    def test_overlay_shouldKeepSourceVersionAndCopyOnlyTrustedDocsConfiguration(self):
        # setup
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            source = root / "source"
            source.mkdir()
            pom = source / "pom.xml"
            pom.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0">'
                           '<version>1.2.0-SNAPSHOT</version><properties/></project>')
            trusted = root / "trusted.xml"
            trusted.write_text('<project xmlns="http://maven.apache.org/POM/4.0.0"><properties>'
                               '<version.maven-javadoc-plugin>3.12.0</version.maven-javadoc-plugin>'
                               '</properties><profiles><profile><id>docs</id></profile></profiles></project>')
            commit = legacy_docs.LEGACY["v1.2.0"][0]
            # execute
            with patch("javadoc.git", return_value=commit):
                checksum = legacy_docs.overlay(source, trusted, "v1.2.0")
            # verify
            result = ET.parse(pom).getroot()
            self.assertEqual("1.2.0-SNAPSHOT", result.findtext("{*}version"))
            self.assertEqual("docs", result.findtext("{*}profiles/{*}profile/{*}id"))
            self.assertEqual(64, len(checksum))


if __name__ == "__main__":
    unittest.main()
