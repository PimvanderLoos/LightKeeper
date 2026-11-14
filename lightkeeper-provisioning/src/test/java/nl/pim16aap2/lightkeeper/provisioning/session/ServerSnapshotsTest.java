package nl.pim16aap2.lightkeeper.provisioning.session;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.assertj.core.api.Assertions.*;

class ServerSnapshotsTest
{
    @Test
    void capture_shouldPublishACompleteIndependentSnapshot(@TempDir Path root)
        throws Exception
    {
        // setup
        final RuntimeManifest original = PreparedServerFixture.create(root);

        // execute
        final Path metadata = ServerSnapshots.capture(
            original,
            root.resolve("snapshots"),
            PreparedServerFixture.javaExecutable()
        );
        final PreparedServerSnapshot snapshot = ServerSnapshots.read(metadata);
        Files.writeString(Path.of(original.serverDirectory()).resolve("plugins/target.jar"), "plugin-v2");

        // verify
        final Path capturedJar = Path.of(snapshot.manifest().serverDirectory()).resolve("plugins/target.jar");
        assertThat(capturedJar).hasContent("plugin-v1");
        assertThat(Files.isSameFile(capturedJar, Path.of(original.serverDirectory()).resolve("plugins/target.jar")))
            .isFalse();
        assertThat(snapshot.javaExecutablePath()).isEqualTo(PreparedServerFixture.javaExecutable());
        assertThat(Path.of(snapshot.manifest().serverJar())).exists();
        assertThat(Path.of(Objects.requireNonNull(snapshot.manifest().agentJar()))).exists();
        try (var entries = Files.list(root.resolve("snapshots")))
        {
            assertThat(entries.map(path -> path.getFileName().toString()))
                .noneMatch(name -> name.startsWith(".pending-"));
        }
    }

    @Test
    void capture_shouldRejectSymlinkInputsAndRemoveIncompleteSnapshot(@TempDir Path root)
        throws Exception
    {
        // setup
        final RuntimeManifest original = PreparedServerFixture.create(root);
        final Path external = Files.writeString(root.resolve("external"), "outside");
        Files.createSymbolicLink(Path.of(original.serverDirectory()).resolve("linked"), external);

        // execute
        final var failure = catchThrowable(() -> ServerSnapshots.capture(
            original, root.resolve("snapshots"), PreparedServerFixture.javaExecutable()
        ));

        // verify
        assertThat(failure).isInstanceOf(ProvisioningException.class).hasRootCauseMessage(
            "Symbolic links are not allowed in IDE preparation inputs: " + external.resolveSibling("prepared/linked")
        );
        assertThat(external).hasContent("outside");
        try (var entries = Files.list(root.resolve("snapshots")))
        {
            assertThat(entries).isEmpty();
        }
    }

    @Test
    void capture_shouldRejectDestinationInsideSource(@TempDir Path root)
        throws Exception
    {
        // setup
        final RuntimeManifest original = PreparedServerFixture.create(root);

        // execute
        final var failure = catchThrowable(() -> ServerSnapshots.capture(
            original, Path.of(original.serverDirectory()).resolve("nested"), PreparedServerFixture.javaExecutable()
        ));

        // verify
        assertThat(failure).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("inside its source");
    }

    @Test
    void read_shouldRejectIncompleteMetadata(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path metadata = Files.writeString(root.resolve("snapshot.json"), "{}");

        // execute
        final var failure = catchThrowable(() -> ServerSnapshots.read(metadata));

        // verify
        assertThat(failure).isInstanceOf(ProvisioningException.class).hasMessageContaining(metadata.toString());
    }

    @Test
    void read_shouldRejectMissingMetadata(@TempDir Path root)
    {
        // setup
        final Path metadata = root.resolve("missing.json");

        // execute
        final var failure = catchThrowable(() -> ServerSnapshots.read(metadata));

        // verify
        assertThat(failure).isInstanceOf(ProvisioningException.class).hasRootCauseMessage(
            "Missing or oversized snapshot metadata: " + metadata
        );
    }
}
