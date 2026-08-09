package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentSyntheticPlayerLifecycleListenerTest
{
    @Test
    void onPlayerDeath_shouldLogAndRetainSyntheticPlayerDeathDetails()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final Logger logger = mock();
        final AgentSyntheticPlayerLifecycleListener listener =
            new AgentSyntheticPlayerLifecycleListener(store, logger);
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        final World world = mock();
        final PlayerDeathEvent event = mock();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("fallingbot");
        when(player.getLocation()).thenReturn(new Location(world, 1.0D, 64.0D, 2.0D));
        when(player.isOnline()).thenReturn(true);
        when(world.getName()).thenReturn("world");
        when(event.getEntity()).thenReturn(player);
        when(event.getDeathMessage()).thenReturn("fallingbot fell from a high place");
        store.registerSyntheticPlayer(uuid, player);

        // execute
        listener.onPlayerDeath(event);

        // verify
        verify(logger).info(contains("LK_AGENT: Synthetic player 'fallingbot'"));
        assertThatThrownBy(() -> store.getRequiredActivePlayer(uuid, "TELEPORT_PLAYER"))
            .isInstanceOfSatisfying(AgentProtocolException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.PLAYER_DEAD))
            .hasMessageContaining("cause=<unknown>")
            .hasMessageContaining("world[1.00, 64.00, 2.00]")
            .hasMessageContaining("fell from a high place");
    }

    @Test
    void onPlayerQuit_shouldLogAndRetainDisconnectedState()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final Logger logger = mock();
        final AgentSyntheticPlayerLifecycleListener listener =
            new AgentSyntheticPlayerLifecycleListener(store, logger);
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        final PlayerQuitEvent event = mock();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.getName()).thenReturn("leavingbot");
        when(event.getPlayer()).thenReturn(player);
        when(event.getQuitMessage()).thenReturn("leavingbot left the game");
        store.registerSyntheticPlayer(uuid, player);

        // execute
        listener.onPlayerQuit(event);

        // verify
        verify(logger).info(contains("LK_AGENT: Synthetic player 'leavingbot'"));
        assertThatThrownBy(() -> store.getRequiredActivePlayer(uuid, "PLAYER_CHAT"))
            .isInstanceOfSatisfying(AgentProtocolException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(AgentErrorCode.PLAYER_DISCONNECTED))
            .hasMessageContaining("left the game");
    }
}
