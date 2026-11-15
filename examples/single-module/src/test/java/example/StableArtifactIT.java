package example;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Coordinates with the external regression runner to rebuild outputs while this session is still running. */
@EnabledIfSystemProperty(named = "consumer.probeDirectory", matches = ".+")
@ExtendWith(LightkeeperExtension.class)
class StableArtifactIT
{
    @Test
    void packagedCommand_shouldKeepItsPrivateRevisionDuringAnotherBuild(ILightkeeperFramework framework)
        throws Exception
    {
        // setup
        final Path probe = Path.of(System.getProperty("consumer.probeDirectory"));
        final Path sessionJar = framework.server().directory().resolve("plugins/consumer-plugin-1.0.jar");
        final byte[] originalJar = Files.readAllBytes(sessionJar);
        final var player = framework.bots().builder()
            .withName("lk_stability")
            .atSpawn(framework.worlds().main())
            .build();

        // execute
        Files.writeString(probe.resolve("session-ready"), "ready");
        assertTimeoutPreemptively(Duration.ofSeconds(120), () ->
        {
            while (!Files.exists(probe.resolve("producer-rebuilt")))
            {
                Thread.sleep(100);
            }
        });
        player.executeCommand("lkversion");

        // verify
        org.assertj.core.api.Assertions.assertThat(Files.readAllBytes(sessionJar)).isEqualTo(originalJar);
        assertThat(player).receivedMessage(System.getProperty("expected.version"));
    }
}
