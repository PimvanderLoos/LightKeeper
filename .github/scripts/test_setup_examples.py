"""Behavioral checks for source-derived consumer setup."""

import unittest
import xml.etree.ElementTree as ET

from setup_examples import presets


class SetupExamplesTest(unittest.TestCase):
    def test_single_module_includes_complete_lifecycle_and_starter(self):
        configuration = ET.fromstring("<project>" + presets()["single"] + "</project>")
        self.assertEqual(configuration.findtext("dependencies/dependency/artifactId"), "lightkeeper-junit-starter")
        self.assertEqual([goal.text for goal in configuration.findall("build/plugins/plugin/executions/execution/goals/goal")],
                         ["lock-build", "prepare-server", "integration-test", "verify"])
        self.assertEqual(configuration.findtext("build/plugins/plugin[artifactId='maven-failsafe-plugin']/configuration/failIfNoTests"), "true")

    def test_reactor_guard_is_inherited_and_only_e2e_prepares(self):
        data = presets()
        parent = ET.fromstring("<project>" + data["parent"] + "</project>")
        e2e = ET.fromstring("<project>" + data["e2e"] + "</project>")
        self.assertEqual([goal.text for goal in parent.findall(".//goal")], ["lock-build"])
        self.assertNotIn("lock-build", [goal.text for goal in e2e.findall(".//goal")])
        self.assertEqual(e2e.findtext("dependencies/dependency/groupId"), "__PRODUCER_GROUP__")
        self.assertEqual(e2e.findtext("build/plugins/plugin/configuration/pluginUnderTest/groupId"), "__PRODUCER_GROUP__")
        self.assertIn("package example;", data["test"])


if __name__ == "__main__":
    unittest.main()
