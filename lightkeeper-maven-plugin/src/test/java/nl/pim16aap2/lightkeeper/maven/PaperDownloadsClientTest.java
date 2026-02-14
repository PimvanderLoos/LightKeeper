package nl.pim16aap2.lightkeeper.maven;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PaperDownloadsClientTest
{
    @Test
    void sortStableVersionsDescending_shouldSortStableSemanticVersions()
    {
        // setup
        final Set<String> versions = new LinkedHashSet<>(List.of(
            "1.21.9",
            "1.21.11",
            "1.20.6",
            "1.21.10",
            "1.21.11-rc1",
            "latest"
        ));

        // execute
        final List<String> sortedVersions = PaperDownloadsClient.sortStableVersionsDescending(versions);

        // verify
        assertThat(sortedVersions)
            .containsExactly("1.21.11", "1.21.10", "1.21.9", "1.20.6");
    }

    @Test
    void sortStableVersionsDescending_shouldIgnoreNonNumericEntriesAndTrimWhitespace()
    {
        // setup
        final Set<String> versions = new LinkedHashSet<>(List.of(
            " 1.21.2 ",
            "1.21.1",
            "dev-build",
            "1.19",
            " 1.21.0-pre1"
        ));

        // execute
        final List<String> sortedVersions = PaperDownloadsClient.sortStableVersionsDescending(versions);

        // verify
        assertThat(sortedVersions)
            .containsExactly("1.21.2", "1.21.1", "1.19");
    }
}
