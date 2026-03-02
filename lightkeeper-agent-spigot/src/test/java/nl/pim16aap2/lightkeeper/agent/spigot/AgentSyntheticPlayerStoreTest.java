package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.nms.api.IBotPlayerNmsAdapter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentSyntheticPlayerStoreTest
{
    @Test
    void getRequiredPlayer_shouldThrowExceptionWhenPlayerIsNotRegistered()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> store.getRequiredPlayer(uuid))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(uuid.toString());
    }

    @Test
    void registerSyntheticPlayer_shouldStorePlayerAndInitializeMessageHistory()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();

        // execute
        store.registerSyntheticPlayer(uuid, player);

        // verify
        assertThat(store.getRequiredPlayer(uuid)).isSameAs(player);
        assertThat(store.getPlayerMessages(uuid)).isEmpty();
        assertThat(store.syntheticPlayerIds()).containsExactly(uuid);
    }

    @Test
    void setPermissions_shouldGrantEachNonBlankPermission()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.addAttachment(plugin)).thenReturn(attachment);

        // execute
        store.setPermissions(plugin, uuid, player, "perm.one, perm.two, ,perm.three");

        // verify
        verify(attachment).setPermission("perm.one", true);
        verify(attachment).setPermission("perm.two", true);
        verify(attachment).setPermission("perm.three", true);
        verifyNoMoreInteractions(attachment);
    }

    @Test
    void removePermissionAttachment_shouldDetachStoredAttachment()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.addAttachment(plugin)).thenReturn(attachment);
        store.setPermissions(plugin, uuid, player, "perm.one");

        // execute
        store.removePermissionAttachment(uuid, player);

        // verify
        verify(player).removeAttachment(attachment);
    }

    @Test
    void sendTrackedMessage_shouldSendMessageAndPersistHistory()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final Player player = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);

        // execute
        store.sendTrackedMessage(player, "hello");

        // verify
        verify(player).sendMessage("hello");
        assertThat(store.getPlayerMessages(uuid)).containsExactly("hello");
    }

    @Test
    void capturePlayerMessages_shouldAppendDrainedMessages()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final IBotPlayerNmsAdapter adapter = mock();
        final UUID uuid = UUID.randomUUID();
        when(adapter.drainReceivedMessages(uuid)).thenReturn(List.of("first", "second"));
        final Player player = mock();
        when(player.getUniqueId()).thenReturn(uuid);
        store.sendTrackedMessage(player, "existing");

        // execute
        store.capturePlayerMessages(adapter, uuid);

        // verify
        assertThat(store.getPlayerMessages(uuid)).containsExactly("existing", "first", "second");
    }

    @Test
    void removeSyntheticPlayer_shouldRemovePlayerAndHistory()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        store.registerSyntheticPlayer(uuid, player);

        // execute
        store.removeSyntheticPlayer(uuid);

        // verify
        assertThat(store.syntheticPlayerIds()).isEmpty();
        assertThat(store.getPlayerMessages(uuid)).isEmpty();
        assertThatThrownBy(() -> store.getRequiredPlayer(uuid))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
