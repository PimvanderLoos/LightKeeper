package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.BotJoinDeniedException;
import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.catchThrowable;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;
import static nl.pim16aap2.lightkeeper.maven.test.FullLoginItSupport.eventsWithPlayerRef;
import static nl.pim16aap2.lightkeeper.maven.test.FullLoginItSupport.offlineUuid;
import static nl.pim16aap2.lightkeeper.maven.test.FullLoginItSupport.playerPosition;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * FULL_LOGIN validation matrix, part 1: login-event contract, denial matrix, and quit/rejoin identity.
 *
 * <p>These tests pin the behaviors only a real login pipeline can deliver — the documented login-event order
 * on both distros, whitelist/ban denials surfaced as typed errors, and the persisted offline identity across a
 * real quit/rejoin cycle. Part 2 ({@link LightkeeperFullLoginSessionIT}) covers the in-session behaviors.
 */
@ExtendWith(LightkeeperExtension.class)
class LightkeeperFullLoginValidationIT
{
    private static final String PRE_LOGIN_EVENT = "org.bukkit.event.player.AsyncPlayerPreLoginEvent";
    private static final String LOGIN_EVENT = "org.bukkit.event.player.PlayerLoginEvent";
    private static final String JOIN_EVENT = "org.bukkit.event.player.PlayerJoinEvent";
    private static final String QUIT_EVENT = "org.bukkit.event.player.PlayerQuitEvent";

    @Test
    @Timeout(60)
    void fullLogin_shouldFireLoginEventsInDocumentedOrderPerDistro(ILightkeeperFramework framework)
    {
        // setup — register all three captures BEFORE joining so no login event can be missed.
        final var world = framework.worlds().main();
        final String name = "lkevorder";
        final UUID expectedUuid = offlineUuid(name);

        try (var preLoginCapture = framework.events().capture(PRE_LOGIN_EVENT);
             var loginCapture = framework.events().capture(LOGIN_EVENT);
             var joinCapture = framework.events().capture(JOIN_EVENT))
        {
            // execute
            final var player = framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .fullLogin()
                .build();

            // verify — all three login events fired exactly once for this bot.
            eventually(Duration.ofSeconds(10), () ->
                assertThat(eventsWithPlayerRef(joinCapture.getCapturedEvents(), expectedUuid)).hasSize(1));

            final List<CapturedEventSnapshot> preLoginEvents = preLoginCapture.getCapturedEvents().stream()
                .filter(event -> new IProtocolValue.PString(name).equals(event.value("getName")))
                .toList();
            final List<CapturedEventSnapshot> loginEvents =
                eventsWithPlayerRef(loginCapture.getCapturedEvents(), expectedUuid);
            final List<CapturedEventSnapshot> joinEvents =
                eventsWithPlayerRef(joinCapture.getCapturedEvents(), expectedUuid);
            assertThat(preLoginEvents).hasSize(1);
            assertThat(loginEvents).hasSize(1);
            assertThat(joinEvents).hasSize(1);

            // The async pre-login carries the server-derived offline UUID; the login result is ALLOWED.
            assertThat(preLoginEvents.getFirst().value("getUniqueId"))
                .isEqualTo(new IProtocolValue.PUuid(expectedUuid));
            assertThat(loginEvents.getFirst().value("getResult"))
                .isInstanceOfSatisfying(IProtocolValue.PEnum.class, result ->
                    assertThat(result.name()).isEqualTo("ALLOWED"));

            // Documented order — AsyncPlayerPreLoginEvent, then PlayerLoginEvent, then PlayerJoinEvent —
            // asserted via tick stamps. This IS the per-distro expectation: the known Paper/Spigot divergence
            // (Paper fires PlayerLoginEvent at configuration-finish, Spigot during the login phase) moves the
            // login event relative to the configuration phase only, never behind the join, so the same
            // ordering holds on both lanes without weakening to an unordered check.
            final long preLoginTick = preLoginEvents.getFirst().tick();
            final long loginTick = loginEvents.getFirst().tick();
            final long joinTick = joinEvents.getFirst().tick();
            assertThat(preLoginTick).isLessThanOrEqualTo(loginTick);
            assertThat(loginTick).isLessThanOrEqualTo(joinTick);

            player.remove();
        }
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldRejectBannedPlayerWithTypedDenial(ILightkeeperFramework framework)
    {
        // setup — ban the name BEFORE it ever joins; the console resolves the offline profile by name.
        final var world = framework.worlds().main();
        final String name = "lkbanned";
        final String banReason = "lk-ban-reason-e2e";
        final CommandResult banResult =
            framework.server().executeCommand(CommandSource.CONSOLE, "ban %s %s".formatted(name, banReason));
        assertThat(banResult.success()).isTrue();

        try
        {
            // execute + verify — the join is denied as a typed error carrying the ban reason.
            assertThatThrownBy(() -> framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .fullLogin()
                .build())
                .isInstanceOf(BotJoinDeniedException.class)
                .hasMessageContaining(banReason);
        }
        finally
        {
            framework.server().executeCommand(CommandSource.CONSOLE, "pardon " + name);
        }
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldRejectWhenWhitelistExcludesPlayer(ILightkeeperFramework framework)
    {
        // setup
        final var world = framework.worlds().main();
        final String name = "lkwhite";
        assertThat(framework.server().executeCommand(CommandSource.CONSOLE, "whitelist on").success()).isTrue();

        try
        {
            // execute + verify — the excluded bot is denied fast, as a typed error (not a join timeout).
            final long startNanos = System.nanoTime();
            final Throwable denial = catchThrowable(() -> framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .fullLogin()
                .build());
            assertThat(denial).isInstanceOf(BotJoinDeniedException.class);
            // Normalized check: Spigot/Paper say "whitelisted", older vanilla strings say "white-listed".
            assertThat(denial.getMessage().toLowerCase(Locale.ROOT).replace("-", ""))
                .as("denial reason should name the whitelist: %s", denial.getMessage())
                .contains("whitelist");

            final long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            assertThat(elapsedMillis)
                .as("a whitelist denial must fail fast instead of riding the join timeout")
                .isLessThan(15_000L);

            // execute + verify — whitelisting the name turns the same join into a success.
            framework.server().executeCommand(CommandSource.CONSOLE, "whitelist add " + name);
            final var player = framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .fullLogin()
                .build();
            assertThat(player.uniqueId()).isEqualTo(offlineUuid(name));
            player.remove();
        }
        finally
        {
            framework.server().executeCommand(CommandSource.CONSOLE, "whitelist remove " + name);
            framework.server().executeCommand(CommandSource.CONSOLE, "whitelist off");
        }
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldRejoinAfterQuitWithSamePersistedIdentity(ILightkeeperFramework framework)
    {
        // setup — join, then give the bot a distinctive item that must survive the quit/rejoin cycle. The
        // persistence observable is the inventory (saved to player data on the real quit path) rather than the
        // position, because FULL_LOGIN honors the requested placement: the agent teleports a (re)joining bot to
        // the requested location/world spawn post-join, overriding the position loaded from player data.
        final var world = framework.worlds().main();
        final String name = "lkrejoin";
        final String persistedItem = "minecraft:diamond";

        final var firstJoin = framework.bots().builder()
            .withName(name)
            .atSpawn(world)
            .fullLogin()
            .build();
        final UUID firstUuid = firstJoin.uniqueId();
        assertThat(framework.server()
            .executeCommand(CommandSource.CONSOLE, "give %s %s 3".formatted(name, persistedItem))
            .success()).isTrue();
        eventually(Duration.ofSeconds(10), () ->
            assertThat(firstJoin.inventory().findItem(persistedItem)).isNotNull());

        // execute — quit (real quit path saves player data), then rejoin under the same name.
        firstJoin.remove();
        eventually(Duration.ofSeconds(10), () ->
            assertThat(playerPosition(world, firstUuid)).isEmpty());
        final var rejoined = framework.bots().builder()
            .withName(name)
            .atSpawn(world)
            .fullLogin()
            .build();

        // verify — same server-derived offline identity, and the inventory was restored from player data.
        assertThat(rejoined.uniqueId()).isEqualTo(firstUuid).isEqualTo(offlineUuid(name));
        eventually(Duration.ofSeconds(10), () ->
            assertThat(rejoined.inventory().findItem(persistedItem)).isNotNull());

        rejoined.remove();
    }

    @Test
    @Timeout(60)
    void fullLogin_shouldFireRealQuitEventOnRemoval(ILightkeeperFramework framework)
    {
        // setup — capture quit events BEFORE removal so the event cannot be missed.
        final var world = framework.worlds().main();
        final String name = "lkquit";

        try (var quitCapture = framework.events().capture(QUIT_EVENT))
        {
            final var player = framework.bots().builder()
                .withName(name)
                .atSpawn(world)
                .fullLogin()
                .build();
            final UUID playerId = player.uniqueId();
            assertThat(eventsWithPlayerRef(quitCapture.getCapturedEvents(), playerId)).isEmpty();

            // execute
            player.remove();

            // verify — removal traverses the real quit path: PlayerQuitEvent fires and the player is gone.
            eventually(Duration.ofSeconds(10), () ->
                assertThat(eventsWithPlayerRef(quitCapture.getCapturedEvents(), playerId)).hasSize(1));
            eventually(Duration.ofSeconds(10), () ->
                assertThat(playerPosition(world, playerId)).isEmpty());
        }
    }
}
