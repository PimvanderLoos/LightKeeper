package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.Lightkeeper;
import nl.pim16aap2.lightkeeper.framework.LightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LightkeeperFrameworkIT
{
    @Test
    void start_shouldExposeValidRuntimeManifestAndMainWorld()
        throws Exception
    {
        // setup
        final Path runtimeManifestPath = getRuntimeManifestPath();
        final RuntimeManifest runtimeManifest = new RuntimeManifestReader().read(runtimeManifestPath);

        // execute
        try (LightkeeperFramework framework = Lightkeeper.start(runtimeManifestPath))
        {
            final WorldHandle worldHandle = framework.mainWorld();

            // verify
            assertThat(runtimeManifest.serverType()).isEqualTo("paper");
            assertThat(runtimeManifest.runtimeProtocolVersion()).isEqualTo("v1.1");
            assertThat(runtimeManifest.udsSocketPath()).isNotBlank();
            assertThat(runtimeManifest.agentAuthToken()).isNotBlank();
            assertThat(runtimeManifest.agentJar()).isNotBlank();
            assertThat(runtimeManifest.agentJarSha256()).hasSize(64);
            assertThat(worldHandle.name()).isNotBlank();
        }
    }

    @Test
    void newWorld_shouldCreateWorldAndSetBlockWhenExecuteCommandIsUsed()
    {
        // setup
        final Path runtimeManifestPath = getRuntimeManifestPath();
        final String worldName = "lk_world_" + UUID.randomUUID().toString().replace("-", "");
        final Vector3Di position = new Vector3Di(1, 70, 1);
        final WorldSpec worldSpec = new WorldSpec(
            worldName,
            WorldSpec.WorldType.FLAT,
            WorldSpec.WorldEnvironment.NORMAL,
            1234L
        );

        // execute
        try (LightkeeperFramework framework = Lightkeeper.start(runtimeManifestPath))
        {
            final WorldHandle worldHandle = framework.newWorld(worldSpec);
            worldHandle.setBlockAt(position, "STONE");
            framework.waitUntil(
                () -> "STONE".equals(worldHandle.blockTypeAt(position)),
                Duration.ofSeconds(20)
            );

            // verify
            assertThat(worldHandle.name()).isEqualTo(worldName);
            assertThat(worldHandle.blockTypeAt(position)).isEqualTo("STONE");
        }
    }

    private static Path getRuntimeManifestPath()
    {
        final String runtimeManifestPath = System.getProperty("lightkeeper.runtimeManifestPath", "").trim();
        assertThat(runtimeManifestPath).isNotBlank();
        return Path.of(runtimeManifestPath);
    }
}
