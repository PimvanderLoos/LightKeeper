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
        store.registerSyntheticPlayer(uuid, player);

        // execute
        store.setPermissions(plugin, uuid, player, "perm.one, perm.two, ,perm.three");

        // verify
        verify(attachment).setPermission("perm.one", true);
        verify(attachment).setPermission("perm.two", true);
        verify(attachment).setPermission("perm.three", true);
        verifyNoMoreInteractions(attachment);
    }

    @Test
    void setPermission_shouldThrowExceptionWhenPlayerIsNotRegistered()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> store.setPermission(plugin, uuid, player, "perm.one", true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(uuid.toString());
    }

    @Test
    void setPermission_shouldGrantPermissionWhenValueIsTrue()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.addAttachment(plugin)).thenReturn(attachment);
        store.registerSyntheticPlayer(uuid, player);

        // execute
        store.setPermission(plugin, uuid, player, "perm.one", true);

        // verify
        verify(attachment).setPermission("perm.one", true);
    }

    @Test
    void setPermission_shouldRevokePermissionWhenValueIsFalse()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.addAttachment(plugin)).thenReturn(attachment);
        store.registerSyntheticPlayer(uuid, player);

        // execute
        store.setPermission(plugin, uuid, player, "perm.one", false);

        // verify
        verify(attachment).setPermission("perm.one", false);
    }

    @Test
    void setPermission_shouldCreateAttachmentOnceWhenCalledTwice()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.addAttachment(plugin)).thenReturn(attachment);
        store.registerSyntheticPlayer(uuid, player);

        // execute
        store.setPermission(plugin, uuid, player, "perm.one", true);
        store.setPermission(plugin, uuid, player, "perm.two", false);

        // verify - the attachment created on the first call is reused, not re-created
        verify(player, times(1)).addAttachment(plugin);
        verify(attachment).setPermission("perm.one", true);
        verify(attachment).setPermission("perm.two", false);
    }

    @Test
    void setPermission_shouldReuseAttachmentCreatedBySetPermissions()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.addAttachment(plugin)).thenReturn(attachment);
        store.registerSyntheticPlayer(uuid, player);
        store.setPermissions(plugin, uuid, player, "perm.bulk");

        // execute
        store.setPermission(plugin, uuid, player, "perm.single", true);

        // verify - the attachment created by the bulk CSV method is reused, not re-created
        verify(player, times(1)).addAttachment(plugin);
        verify(attachment).setPermission("perm.single", true);
    }

    @Test
    void unsetPermission_shouldThrowExceptionWhenPlayerIsNotRegistered()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final UUID uuid = UUID.randomUUID();

        // execute + verify
        assertThatThrownBy(() -> store.unsetPermission(uuid, "perm.one"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(uuid.toString());
    }

    @Test
    void unsetPermission_shouldBeNoOpWhenNoAttachmentWasSet()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final Player player = mock();
        final UUID uuid = UUID.randomUUID();
        store.registerSyntheticPlayer(uuid, player);

        // execute + verify - should not throw
        store.unsetPermission(uuid, "perm.one");
        verifyNoInteractions(player);
    }

    @Test
    void unsetPermission_shouldCallUnsetPermissionOnStoredAttachment()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final JavaPlugin plugin = mock();
        final Player player = mock();
        final PermissionAttachment attachment = mock();
        final UUID uuid = UUID.randomUUID();
        when(player.addAttachment(plugin)).thenReturn(attachment);
        store.registerSyntheticPlayer(uuid, player);
        store.setPermission(plugin, uuid, player, "perm.one", true);

        // execute
        store.unsetPermission(uuid, "perm.one");

        // verify
        verify(attachment).unsetPermission("perm.one");
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
        store.registerSyntheticPlayer(uuid, player);
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
        store.registerSyntheticPlayer(uuid, player);

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
        store.registerSyntheticPlayer(uuid, player);
        store.sendTrackedMessage(player, "existing");

        // execute
        store.capturePlayerMessages(adapter, uuid);

        // verify
        assertThat(store.getPlayerMessages(uuid)).containsExactly("existing", "first", "second");
    }

    @Test
    void capturePlayerChatComponents_shouldAppendDrainedComponents()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final IBotPlayerNmsAdapter adapter = mock();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        when(adapter.drainChatComponents(uuid)).thenReturn(List.of("{\"text\":\"hello\"}"));
        store.registerSyntheticPlayer(uuid, player);

        // execute
        store.capturePlayerChatComponents(adapter, uuid);

        // verify
        assertThat(store.getPlayerChatComponents(uuid)).containsExactly("{\"text\":\"hello\"}");
    }

    @Test
    void capturePlayerChatComponents_shouldKeepHistoryWhenNoComponentsWereDrained()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final IBotPlayerNmsAdapter adapter = mock();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        when(adapter.drainChatComponents(uuid))
            .thenReturn(List.of("{\"text\":\"hello\"}"))
            .thenReturn(List.of());
        store.registerSyntheticPlayer(uuid, player);
        store.capturePlayerChatComponents(adapter, uuid);

        // execute
        store.capturePlayerChatComponents(adapter, uuid);

        // verify
        assertThat(store.getPlayerChatComponents(uuid)).containsExactly("{\"text\":\"hello\"}");
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

    @Test
    void capturePlayerMessages_shouldNotAppendWhenDrainReturnsEmpty()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final IBotPlayerNmsAdapter adapter = mock();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();
        when(player.getUniqueId()).thenReturn(uuid);
        when(adapter.drainReceivedMessages(uuid)).thenReturn(List.of());
        store.registerSyntheticPlayer(uuid, player);
        store.sendTrackedMessage(player, "existing");

        // execute
        store.capturePlayerMessages(adapter, uuid);

        // verify - history unchanged when drain is empty
        assertThat(store.getPlayerMessages(uuid)).containsExactly("existing");
    }

    @Test
    void removePermissionAttachment_shouldBeNoOpWhenNoAttachmentWasSet()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();
        final UUID uuid = UUID.randomUUID();
        final Player player = mock();

        // execute + verify - should not throw
        store.removePermissionAttachment(uuid, player);
        verifyNoInteractions(player);
    }

    @Test
    void getPlayerChatComponents_shouldReturnEmptyListForUnknownPlayer()
    {
        // setup
        final AgentSyntheticPlayerStore store = new AgentSyntheticPlayerStore();

        // execute
        final List<String> components = store.getPlayerChatComponents(UUID.randomUUID());

        // verify
        assertThat(components).isEmpty();
    }
}
