package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PR1 smoke tests for the FULL_LOGIN bot driver, run on both the Paper and Spigot integration lanes.
 *
 * <p>These prove the reflective login pipeline (LK-18) end-to-end on both distributions and guard the
 * default legacy-spawn path against regressions from the driver's introduction.
 */
@ExtendWith(LightkeeperExtension.class)
class LightkeeperFullLoginIT
{
    private static UUID offlineUuid(String name)
    {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void fullLogin_shouldJoinThroughRealLoginPipelineOnBothDistros(ILightkeeperFramework framework)
    {
        // setup — register the event captures BEFORE joining so the login events cannot be missed.
        final var world = framework.worlds().main();
        final String name = "lkfulllogin";

        try (var preLoginCapture =
                 framework.events().capture("org.bukkit.event.player.AsyncPlayerPreLoginEvent");
             var joinCapture =
                 framework.events().capture("org.bukkit.event.player.PlayerJoinEvent"))
        {
            // execute — a full-login join drives the real handshake/login/configuration/play pipeline.
            final var player = framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .withLocale("en_us")
                .fullLogin()
                .build();

            // verify — the bot exists with the server-derived offline UUID (FULL_LOGIN ignores any builder UUID).
            assertThat(player.name()).isEqualTo(name);
            assertThat(player.uniqueId()).isEqualTo(offlineUuid(name));

            // Both the async pre-login and the (main-thread) join events must have fired for a real login.
            framework.waitUntil(() -> !joinCapture.getCapturedEvents().isEmpty(), Duration.ofSeconds(10));
            assertThat(joinCapture.getCapturedEvents()).isNotEmpty();
            assertThat(preLoginCapture.getCapturedEvents()).isNotEmpty();

            player.remove();
        }
    }

    @Test
    void legacySpawn_shouldStillSpawnBotsAfterDriverIntroduction(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final String name = "lklegacyspawn";

        // execute — the default builder path stays LEGACY_SPAWN (no fullLogin()).
        final var player = framework.bots().builder()
            .withName(name)
            .atSpawn(world)
            .build();

        // verify — the legacy reflective spawn still produces a usable, driveable bot.
        assertThat(player.name()).isEqualTo(name);
        assertThat(player.uniqueId()).isNotNull();
        player.teleport(world, 0, 101, 0);

        player.remove();
    }
}
