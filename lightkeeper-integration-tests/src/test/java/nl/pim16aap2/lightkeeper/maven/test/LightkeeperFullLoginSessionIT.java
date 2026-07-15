package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.Vec3;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static nl.pim16aap2.lightkeeper.maven.test.FullLoginItSupport.assertPositionCloseTo;
import static nl.pim16aap2.lightkeeper.maven.test.FullLoginItSupport.eventsWithPlayerRef;
import static nl.pim16aap2.lightkeeper.maven.test.FullLoginItSupport.offlineUuid;
import static nl.pim16aap2.lightkeeper.maven.test.FullLoginItSupport.playerPosition;

/**
 * FULL_LOGIN validation matrix, part 2: in-session behaviors of fully logged-in bots.
 *
 * <p>Covers the driver's teleport acknowledgement, login under the (default-on) compression threshold,
 * multiple simultaneous sessions, the configuration-phase locale, chat parity with the S4 event path, and the
 * LuckPerms Permissible-injection survival that motivated the full-login pipeline in the first place. Part 1
 * ({@link LightkeeperFullLoginValidationIT}) covers the login-event contract and the denial matrix.
 */
@ExtendWith(LightkeeperExtension.class)
class LightkeeperFullLoginSessionIT
{
    private static final String CHAT_EVENT = "org.bukkit.event.player.AsyncPlayerChatEvent";
    private static final String QUIT_EVENT = "org.bukkit.event.player.PlayerQuitEvent";

    @Test
    @Timeout(60)
    void fullLogin_shouldAckTeleportAfterJoin(ILightkeeperFramework framework)
    {
        // setup — watch for kicks: a driver that failed to acknowledge the teleport would desync the session.
        final var world = framework.worlds().main();
        final String name = "lktpack";
        final Vec3 target = new Vec3(33.0, 120.0, 33.0);

        try (var quitCapture = framework.events().capture(QUIT_EVENT))
        {
            final var player = framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .fullLogin()
                .build();

            // execute — the teleport makes the server send a position packet that the driver must answer with
            // the teleport-accept packet for the session to stay in sync.
            player.teleport(world, target);

            // verify — the position is applied server-side and the session survives the ack round trip.
            eventually(Duration.ofSeconds(10), () ->
                assertThat(playerPosition(world, player.uniqueId())).hasValueSatisfying(position ->
                    assertPositionCloseTo(position, target)));
            player.andWaitTicks(20);
            assertThat(eventsWithPlayerRef(quitCapture.getCapturedEvents(), player.uniqueId())).isEmpty();

            player.remove();
        }
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldNegotiateCompressionWhenEnabled(ILightkeeperFramework framework)
        throws IOException
    {
        // setup — the provisioner leaves the vanilla default network-compression-threshold=256 in place, so
        // compression is negotiated on every login. Assert that explicitly: if provisioning ever flipped it
        // to -1 (disabled), this test must fail instead of silently no longer covering the driver's
        // compression path. A per-test server.properties override is not possible today: the config overlay
        // is copied wholesale after the runtime-port rewrite, so overlaying server.properties would clobber
        // the reserved port.
        final Path serverProperties = framework.server().directory().resolve("server.properties");
        final int compressionThreshold = readCompressionThreshold(serverProperties);
        assertThat(compressionThreshold)
            .as("network-compression-threshold in %s (>= 0 means compression is enabled)", serverProperties)
            .isGreaterThanOrEqualTo(0);

        // execute — the join must handle the login-phase compression packet; everything after it (the whole
        // configuration phase, the join, and the chat below) rides the compressed framing.
        final var world = framework.worlds().main();
        final String message = "compressed chat roundtrip";
        try (var chatCapture = framework.events().capture(CHAT_EVENT))
        {
            final var player = framework.bots().builder()
                .withName("lkcompress")
                .atSpawn(world)
                .fullLogin()
                .build();
            player.chat(message);

            // verify
            eventually(Duration.ofSeconds(10), () ->
                assertThat(chatCapture.getCapturedEvents()).anySatisfy(event ->
                    assertThat(event.value("getMessage")).isEqualTo(new IProtocolValue.PString(message))));

            player.remove();
        }
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldSupportFiveConcurrentJoins(ILightkeeperFramework framework)
    {
        // setup — the framework transport (UdsAgentTransport.send) is synchronized: exactly one request may
        // be in flight per agent connection, so wire-level concurrent joins are not deliverable through this
        // framework today. The five bots therefore join back-to-back, and the assertions target what full
        // logins must guarantee regardless: five simultaneously online sessions with distinct server-derived
        // identities and no cross-talk between their driver sessions.
        final var world = framework.worlds().main();
        final List<PlayerHandle> bots = new ArrayList<>();

        try
        {
            // execute
            for (int index = 1; index <= 5; ++index)
            {
                bots.add(framework.bots().builder()
                    .withName("lkconc" + index)
                    .atSpawn(world)
                    .fullLogin()
                    .build());
            }

            // verify — five distinct offline UUIDs, each derived from its own name...
            final Set<UUID> uniqueIds = bots.stream().map(PlayerHandle::uniqueId).collect(Collectors.toSet());
            assertThat(uniqueIds).hasSize(5);
            for (final PlayerHandle bot : bots)
                assertThat(bot.uniqueId()).isEqualTo(offlineUuid(bot.name()));

            // ...all simultaneously online, observed in one internally consistent entity-query burst.
            eventually(Duration.ofSeconds(10), () ->
            {
                final Set<UUID> onlineIds = world.entities().ofType("minecraft:player").snapshot().stream()
                    .map(EntitySnapshot::uuid)
                    .collect(Collectors.toSet());
                assertThat(onlineIds).containsAll(uniqueIds);
            });
        }
        finally
        {
            for (final PlayerHandle bot : bots)
                bot.remove();
        }
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldApplyRequestedLocale(ILightkeeperFramework framework)
    {
        // setup — the driver sends the locale inside ClientInformation during the configuration phase; the
        // test plugin's /lktestlocale logs Player#getLocale, so the server-side view of the locale is
        // asserted from the captured console output.
        final var world = framework.worlds().main();
        final String name = "lklocale";

        final var player = framework.bots().builder()
            .withName(name)
            .atSpawn(world)
            .withLocale("de_de")
            .fullLogin()
            .build();

        // execute
        player.executeCommand("lktestlocale");

        // verify
        eventually(Duration.ofSeconds(10), () ->
            assertThat(framework.server().output())
                .anyMatch(line -> line.contains("LK_LOCALE name=%s locale=de_de".formatted(name))));

        player.remove();
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldChatLikeARealClient(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final String name = "lkfullchat";
        final String message = "hello from a real login";

        try (var chatCapture = framework.events().capture(CHAT_EVENT))
        {
            final var player = framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .fullLogin()
                .build();

            // execute — chat parity with the S4 path, now from a fully logged-in session.
            player.chat(message);

            // verify — the real AsyncPlayerChatEvent fires, attributed to the full-login bot.
            eventually(Duration.ofSeconds(10), () ->
                assertThat(chatCapture.getCapturedEvents()).isNotEmpty());
            final CapturedEventSnapshot chatEvent = chatCapture.getCapturedEvents().getFirst();
            assertThat(chatEvent.value("getMessage")).isEqualTo(new IProtocolValue.PString(message));
            assertThat(chatEvent.value("getPlayer"))
                .isInstanceOfSatisfying(IProtocolValue.PRef.class, playerRef ->
                    assertThat(playerRef.id()).isEqualTo(player.uniqueId().toString()));

            player.remove();
        }
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldRetainLuckPermsPermissionInjectedAtLogin(ILightkeeperFramework framework)
    {
        // setup — LuckPerms is provisioned by this module's pom (Modrinth, pinned version); fail fast with a
        // clear reason when that wiring is missing rather than timing out on the permission check below.
        assertThat(framework.server().directory().resolve("plugins").resolve("LuckPerms.jar"))
            .as("LuckPerms must be provisioned for the Permissible-injection survival check")
            .isRegularFile();

        final var world = framework.worlds().main();
        final String name = "lklperm";
        final String node = "lightkeeper.test.lpnode";

        final var player = framework.bots().builder()
            .withName(name)
            .atSpawn(world)
            .fullLogin()
            .build();
        assertThat(player.permissions().has(node)).isFalse();

        try
        {
            // execute — grant through LuckPerms itself (console), NOT LightKeeper's own attachment.
            final CommandResult grantResult = framework.server().executeCommand(
                CommandSource.CONSOLE, "lp user %s permission set %s true".formatted(name, node));
            assertThat(grantResult.success()).isTrue();

            // verify — the LIVE hasPermission resolves through the Permissible LuckPerms injected at
            // PlayerLoginEvent. Had the injection not survived onto the joined player instance, the
            // LuckPerms-stored node would keep reading false here. LuckPerms applies console edits
            // asynchronously, hence the retry window.
            eventually(Duration.ofSeconds(20), () ->
                assertThat(player.permissions().has(node)).isTrue());
        }
        finally
        {
            framework.server().executeCommand(
                CommandSource.CONSOLE, "lp user %s permission unset %s".formatted(name, node));
            player.remove();
        }
    }

    private static int readCompressionThreshold(Path serverProperties)
        throws IOException
    {
        for (final String line : Files.readAllLines(serverProperties, StandardCharsets.UTF_8))
        {
            final String trimmed = line.trim();
            if (trimmed.startsWith("network-compression-threshold="))
                return Integer.parseInt(trimmed.substring("network-compression-threshold=".length()).trim());
        }
        throw new AssertionError("No network-compression-threshold entry in " + serverProperties);
    }
}
