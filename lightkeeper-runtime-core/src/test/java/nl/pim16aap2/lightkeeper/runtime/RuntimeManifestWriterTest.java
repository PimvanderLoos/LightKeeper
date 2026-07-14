package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RuntimeManifestWriterTest
{
    @Test
    void write_shouldPersistRuntimeManifestAsReadableJson(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final RuntimeManifest runtimeManifest = new RuntimeManifest(
            "paper",
            "1.21.11",
            116L,
            "cache-key",
            "/tmp/server",
            "/tmp/server/paper.jar",
            2048,
            "/tmp/lightkeeper.sock",
            "auth-token",
            "/tmp/plugins/lightkeeper-agent-spigot.jar",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            RuntimeProtocol.VERSION,
            "agent-cache-id",
            "-Dfoo=bar",
            List.of(
                new RuntimeManifest.ProvisionedWorld("fixture-world", "NORMAL", "FLAT", 42L, true),
                new RuntimeManifest.ProvisionedWorld("template-world", "NORMAL", "NORMAL", 0L, false)
            )
        );
        final Path manifestPath = tempDirectory.resolve("runtime-manifest.json");

        // execute
        new RuntimeManifestWriter().write(runtimeManifest, manifestPath);
        final RuntimeManifest parsedManifest = new RuntimeManifestReader().read(manifestPath);

        // verify
        assertThat(parsedManifest.serverType()).isEqualTo("paper");
        assertThat(parsedManifest.serverVersion()).isEqualTo("1.21.11");
        assertThat(parsedManifest.paperBuildId()).isEqualTo(116L);
        assertThat(parsedManifest.extraJvmArgs()).isEqualTo("-Dfoo=bar");
        assertThat(parsedManifest.provisionedWorlds()).hasSize(2);
        assertThat(parsedManifest.provisionedWorlds().get(0).name()).isEqualTo("fixture-world");
        assertThat(parsedManifest.provisionedWorlds().get(0).worldType()).isEqualTo("FLAT");
        assertThat(parsedManifest.provisionedWorlds().get(0).loadOnStartup()).isTrue();
        assertThat(parsedManifest.provisionedWorlds().get(1).name()).isEqualTo("template-world");
        assertThat(parsedManifest.provisionedWorlds().get(1).loadOnStartup()).isFalse();
    }
}
