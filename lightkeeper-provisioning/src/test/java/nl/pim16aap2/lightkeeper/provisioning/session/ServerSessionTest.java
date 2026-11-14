package nl.pim16aap2.lightkeeper.provisioning.session;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class ServerSessionTest
{
    @Test
    void create_shouldIsolateFilesPortsSocketsAndTokens(@TempDir Path root)
        throws Exception
    {
        // setup
        final PreparedServerSnapshot snapshot = PreparedServerFixture.snapshot(root);
        final Path sessions = root.resolve("sessions");

        // execute
        try (var first = ServerSession.create(snapshot, sessions);
             var second = ServerSession.create(snapshot, sessions))
        {
            final var firstManifest = new RuntimeManifestReader().read(first.manifestPath());
            final var secondManifest = new RuntimeManifestReader().read(second.manifestPath());
            final Path firstJar = Path.of(firstManifest.serverDirectory()).resolve("plugins/target.jar");
            Files.writeString(firstJar, "changed-in-first-session");
            Files.writeString(Path.of(firstManifest.serverDirectory()).resolve("world/level.dat"), "dirty-world");

            // verify
            assertThat(Path.of(secondManifest.serverDirectory()).resolve("plugins/target.jar")).hasContent("plugin-v1");
            assertThat(Path.of(secondManifest.serverDirectory()).resolve("world/level.dat")).hasContent("clean-world");
            assertThat(Path.of(snapshot.manifest().serverDirectory()).resolve("world/level.dat"))
                .hasContent("clean-world");
            final Path snapshotJar = Path.of(snapshot.manifest().serverDirectory()).resolve("plugins/target.jar");
            assertThat(Files.isSameFile(firstJar, snapshotJar))
                .isFalse();
            assertThat(Files.isSymbolicLink(firstJar)).isFalse();
            assertThat(firstManifest.agentAuthToken()).isNotEqualTo(secondManifest.agentAuthToken());
            assertThat(firstManifest.udsSocketPath()).isNotEqualTo(secondManifest.udsSocketPath());
            assertThat(firstManifest.extraJvmArgs()).isEqualTo("-Dfixture=true");
            assertThat(firstManifest.javaExecutablePath()).isEqualTo(PreparedServerFixture.javaExecutable());
            assertThat(Path.of(firstManifest.serverDirectory()).resolve("server.properties"))
                .content().doesNotContain("port=4242");
        }
    }

    @Test
    void create_shouldRejectChangedSnapshotWithoutFallback(@TempDir Path root)
        throws Exception
    {
        // setup
        final PreparedServerSnapshot snapshot = PreparedServerFixture.snapshot(root);
        Files.writeString(Path.of(snapshot.manifest().serverDirectory()).resolve("plugins/target.jar"), "tampered");

        // execute
        final var failure = catchThrowable(() -> ServerSession.create(snapshot, root.resolve("sessions")));

        // verify
        assertThat(failure).isInstanceOf(ProvisioningException.class)
            .hasRootCauseMessage(
                "Prepared server snapshot changed before copying: " + snapshot.manifest().serverDirectory()
            );
        try (var entries = Files.list(root.resolve("sessions")))
        {
            assertThat(entries).isEmpty();
        }
    }

    @Test
    void close_shouldDeleteOnlyTheOwnedSessionAndBeIdempotent(@TempDir Path root)
        throws Exception
    {
        // setup
        final PreparedServerSnapshot snapshot = PreparedServerFixture.snapshot(root);
        final ServerSession session = ServerSession.create(snapshot, root.resolve("sessions"));
        final Path other = Files.createDirectory(root.resolve("sessions/other"));

        // execute
        session.close();
        session.close();

        // verify
        assertThat(session.directory()).doesNotExist();
        assertThat(other).isDirectory();
        assertThat(Path.of(snapshot.manifest().serverDirectory())).isDirectory();
    }

    @Test
    void preserve_shouldRetainFailureDiagnosticsAfterClose(@TempDir Path root)
        throws Exception
    {
        // setup
        final ServerSession session = ServerSession.create(
            PreparedServerFixture.snapshot(root),
            root.resolve("sessions")
        );
        final Path diagnostic = Files.writeString(session.directory().resolve("failure.log"), "startup failed");

        // execute
        session.preserve();
        session.close();

        // verify
        assertThat(diagnostic).hasContent("startup failed");
    }
}
