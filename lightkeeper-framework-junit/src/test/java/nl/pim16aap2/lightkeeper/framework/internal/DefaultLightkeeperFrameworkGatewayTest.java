package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.DropResult;
import nl.pim16aap2.lightkeeper.protocol.GetCapturedEvents;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;
import nl.pim16aap2.lightkeeper.protocol.MutatePlayerPermission;
import nl.pim16aap2.lightkeeper.protocol.QueryEntities;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultLightkeeperFrameworkGatewayTest
{
    @Test
    void grantPermission_shouldDelegateToAgentClientWithGrantMode()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );
        final UUID playerId = UUID.randomUUID();

        // execute
        framework.grantPermission(playerId, "lightkeeper.fly");

        // verify
        verify(agentClient).mutatePlayerPermission(playerId, "lightkeeper.fly", MutatePlayerPermission.Mode.GRANT);
    }

    @Test
    void revokePermission_shouldDelegateToAgentClientWithRevokeMode()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );
        final UUID playerId = UUID.randomUUID();

        // execute
        framework.revokePermission(playerId, "lightkeeper.fly");

        // verify
        verify(agentClient).mutatePlayerPermission(playerId, "lightkeeper.fly", MutatePlayerPermission.Mode.REVOKE);
    }

    @Test
    void unsetPermission_shouldDelegateToAgentClientWithUnsetMode()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );
        final UUID playerId = UUID.randomUUID();

        // execute
        framework.unsetPermission(playerId, "lightkeeper.fly");

        // verify
        verify(agentClient).mutatePlayerPermission(playerId, "lightkeeper.fly", MutatePlayerPermission.Mode.UNSET);
    }

    @Test
    void grantPermission_shouldThrowExceptionWhenPermissionIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.grantPermission(UUID.randomUUID(), "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void grantPermission_shouldThrowNullPointerExceptionWhenPlayerIdIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.grantPermission(null, "lightkeeper.fly"))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void hasPermission_shouldReturnValueFromAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        when(agentClient.hasPlayerPermission(playerId, "lightkeeper.fly")).thenReturn(true);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.hasPermission(playerId, "lightkeeper.fly");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void serverDirectory_shouldReturnPathFromManifest()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute
        final Path serverDirectory = framework.server().directory();

        // verify
        assertThat(serverDirectory).isEqualTo(Path.of("/tmp/lightkeeper/server"));
    }

    @Test
    void pluginDataDirectory_shouldResolvePluginsSubdirectory()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute
        final Path pluginDataDirectory = framework.server().pluginDataDirectory("AnimatedArchitecture");

        // verify
        assertThat(pluginDataDirectory)
            .isEqualTo(Path.of("/tmp/lightkeeper/server", "plugins", "AnimatedArchitecture"));
    }

    @Test
    void pluginDataDirectory_shouldThrowExceptionWhenNameIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.server().pluginDataDirectory("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void pluginDataDirectory_shouldThrowExceptionWhenNameContainsParentTraversal()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.server().pluginDataDirectory("../evil"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pluginDataDirectory_shouldThrowExceptionWhenNameContainsSlash()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.server().pluginDataDirectory("a/b"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void newWorldFromTemplate_shouldCreateWorldFromProvisionedTemplate()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.newWorld(new WorldSpec(
            "template-a", WorldSpec.WorldType.FLAT, WorldSpec.WorldEnvironment.NETHER, 7L)))
            .thenReturn("template-a-instance");
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifestWithProvisionedWorlds(List.of(
                new RuntimeManifest.ProvisionedWorld("template-a", "NETHER", "FLAT", 7L, false)
            )),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final WorldHandle result = framework.worlds().fromTemplate("template-a");

        // verify
        assertThat(result.name()).isEqualTo("template-a-instance");
        verify(agentClient).newWorld(new WorldSpec(
            "template-a", WorldSpec.WorldType.FLAT, WorldSpec.WorldEnvironment.NETHER, 7L));
    }

    @Test
    void newWorldFromTemplate_shouldThrowExceptionWhenTemplateIsNotProvisioned()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifestWithProvisionedWorlds(List.of(
                new RuntimeManifest.ProvisionedWorld("template-a", "NORMAL", "NORMAL", 0L, false),
                new RuntimeManifest.ProvisionedWorld("template-b", "NORMAL", "NORMAL", 0L, false)
            )),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.worlds().fromTemplate("template-x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("template-a")
            .hasMessageContaining("template-b");
    }

    @Test
    void newWorldFromTemplate_shouldThrowExceptionWhenTemplateNameIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.worlds().fromTemplate("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void newWorldFromTemplate_shouldThrowNullPointerExceptionWhenTemplateNameIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.worlds().fromTemplate(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void leftClickBlock_shouldReturnTrueWhenAgentClientReportsCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final BlockPos position = new BlockPos(1, 64, 1);
        when(agentClient.leftClickBlock(playerId, position, "UP")).thenReturn(true);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.leftClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void leftClickBlock_shouldReturnFalseWhenAgentClientReportsNotCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final BlockPos position = new BlockPos(1, 64, 1);
        when(agentClient.leftClickBlock(playerId, position, "UP")).thenReturn(false);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.leftClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void rightClickBlock_shouldReturnTrueWhenAgentClientReportsCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final BlockPos position = new BlockPos(1, 64, 1);
        when(agentClient.rightClickBlock(playerId, position, "UP")).thenReturn(true);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.rightClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void rightClickBlock_shouldReturnFalseWhenAgentClientReportsNotCancelled()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        final BlockPos position = new BlockPos(1, 64, 1);
        when(agentClient.rightClickBlock(playerId, position, "UP")).thenReturn(false);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final boolean result = framework.rightClickBlock(playerId, position, "UP");

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void dropItem_shouldReturnDropResultFromAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID playerId = UUID.randomUUID();
        when(agentClient.dropItem(playerId)).thenReturn(DropResult.EMPTY_HAND);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final DropResult result = framework.dropItem(playerId);

        // verify
        assertThat(result).isEqualTo(DropResult.EMPTY_HAND);
    }

    @Test
    void getBlockData_shouldReturnValueFromAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final BlockPos position = new BlockPos(1, 64, 1);
        when(agentClient.blockData("world", position)).thenReturn("minecraft:lever[powered=true]");
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final String result = framework.getBlockData("world", position);

        // verify
        assertThat(result).isEqualTo("minecraft:lever[powered=true]");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void getBlockData_shouldThrowNullPointerExceptionWhenWorldNameIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.getBlockData(null, new BlockPos(0, 0, 0)))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void setBlockData_shouldDelegateToAgentClientWithTrimmedBlockData()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final BlockPos position = new BlockPos(1, 64, 1);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        framework.setBlockData("world", position, "  minecraft:lever[powered=true]  ");

        // verify
        verify(agentClient).setBlockData("world", position, "minecraft:lever[powered=true]");
    }

    @Test
    void setBlockData_shouldThrowExceptionWhenBlockDataIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );
        final BlockPos position = new BlockPos(1, 64, 1);

        // execute + verify
        assertThatThrownBy(() -> framework.setBlockData("world", position, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void setBlockData_shouldThrowNullPointerExceptionWhenBlockDataIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );
        final BlockPos position = new BlockPos(1, 64, 1);

        // execute + verify
        assertThatThrownBy(() -> framework.setBlockData("world", position, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void cancelNextEvents_shouldDelegateToAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        framework.cancelNextEvents("org.bukkit.event.player.PlayerJoinEvent", 3);

        // verify
        verify(agentClient).cancelNextEvents("org.bukkit.event.player.PlayerJoinEvent", 3);
    }

    @Test
    void cancelNextEvents_shouldThrowExceptionWhenCountIsNotPositive()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.cancelNextEvents("org.bukkit.event.player.PlayerJoinEvent", 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("positive");
    }

    @Test
    void playerChat_shouldDelegateToAgentClientPreservingWhitespace()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );
        final java.util.UUID playerId = java.util.UUID.randomUUID();

        // execute
        framework.playerChat(playerId, "  spaced message  ");

        // verify - intentional whitespace reaches the agent unchanged
        verify(agentClient).playerChat(playerId, "  spaced message  ");
    }

    @Test
    void playerChat_shouldThrowExceptionWhenMessageIsBlank()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.playerChat(UUID.randomUUID(), "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    void currentServerTick_shouldReturnValueFromAgentClient()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        when(agentClient.getServerTick()).thenReturn(123L);
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final long result = framework.server().currentTick();

        // verify
        assertThat(result).isEqualTo(123L);
    }

    @Test
    void getCapturedEvents_shouldMapTickIntoSnapshot()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final Map<String, IProtocolValue> values = Map.of("isCancelled", new IProtocolValue.PBool(true));
        final GetCapturedEvents.CapturedEvent capturedEvent = new GetCapturedEvents.CapturedEvent(9L, values);
        when(agentClient.getCapturedEvents("org.bukkit.event.player.PlayerJoinEvent"))
            .thenReturn(List.of(capturedEvent));
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final List<CapturedEventSnapshot> result =
            framework.getCapturedEvents("org.bukkit.event.player.PlayerJoinEvent");

        // verify
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().eventClassName()).isEqualTo("org.bukkit.event.player.PlayerJoinEvent");
        assertThat(result.getFirst().tick()).isEqualTo(9L);
        assertThat(result.getFirst().values()).isEqualTo(values);
    }

    @Test
    void countEntities_shouldDelegateToAgentClientWithCountOnlyTrue()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final BlockPos min = new BlockPos(0, 0, 0);
        final BlockPos max = new BlockPos(10, 10, 10);
        when(agentClient.queryEntities(eq("world"), eq("minecraft:zombie"), eq(min), eq(max), eq(true)))
            .thenReturn(new QueryEntities.Response(5L, 3, List.of()));
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final int result = framework.countEntities("world", "minecraft:zombie", min, max);

        // verify
        assertThat(result).isEqualTo(3);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void countEntities_shouldThrowNullPointerExceptionWhenWorldNameIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.countEntities(null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void snapshotEntities_shouldMapEntityDataIncludingTransformAndThreadTickIntoEverySnapshot()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final UUID entityId = UUID.randomUUID();
        final QueryEntities.TransformData transformData = new QueryEntities.TransformData(
            1.0, 2.0, 3.0, 1.5, 1.5, 1.5, List.of(0.1, 0.2, 0.3, 0.4), List.of(0.5, 0.6, 0.7, 0.8));
        final QueryEntities.EntityData entityData = new QueryEntities.EntityData(
            entityId, "minecraft:block_display", 10.0, 64.0, -5.0, "Display",
            List.of("plugin:alpha"), transformData);
        when(agentClient.queryEntities(eq("world"), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(new QueryEntities.Response(99L, 1, List.of(entityData)));
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final List<EntitySnapshot> result = framework.snapshotEntities("world", null, null, null);

        // verify
        assertThat(result).singleElement().satisfies(snapshot ->
        {
            assertThat(snapshot.uuid()).isEqualTo(entityId);
            assertThat(snapshot.typeKey()).isEqualTo("minecraft:block_display");
            assertThat(snapshot.position()).isEqualTo(new nl.pim16aap2.lightkeeper.framework.Vec3(10.0, 64.0, -5.0));
            assertThat(snapshot.customName()).isEqualTo("Display");
            assertThat(snapshot.pdcKeys()).containsExactly("plugin:alpha");
            assertThat(snapshot.tick()).isEqualTo(99L);
            final EntitySnapshot.Transform transform = Objects.requireNonNull(snapshot.transform());
            assertThat(transform.translation())
                .isEqualTo(new nl.pim16aap2.lightkeeper.framework.Vec3(1.0, 2.0, 3.0));
            assertThat(transform.scale())
                .isEqualTo(new nl.pim16aap2.lightkeeper.framework.Vec3(1.5, 1.5, 1.5));
            assertThat(transform.leftRotation())
                .isEqualTo(new EntitySnapshot.Rotation(0.1, 0.2, 0.3, 0.4));
            assertThat(transform.rightRotation())
                .isEqualTo(new EntitySnapshot.Rotation(0.5, 0.6, 0.7, 0.8));
        });
    }

    @Test
    void snapshotEntities_shouldPassThroughNullTransformForNonDisplayEntity()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final QueryEntities.EntityData entityData = new QueryEntities.EntityData(
            UUID.randomUUID(), "minecraft:zombie", 1.0, 2.0, 3.0, null, List.of(), null);
        when(agentClient.queryEntities(eq("world"), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(new QueryEntities.Response(1L, 1, List.of(entityData)));
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute
        final List<EntitySnapshot> result = framework.snapshotEntities("world", null, null, null);

        // verify
        assertThat(result).singleElement().extracting(EntitySnapshot::transform).isNull();
    }

    @Test
    void snapshotEntities_shouldThrowIllegalStateExceptionWhenRotationListSizeIsNotFour()
    {
        // setup
        final UdsAgentClient agentClient = mock(UdsAgentClient.class);
        final QueryEntities.TransformData badTransform = new QueryEntities.TransformData(
            0.0, 0.0, 0.0, 1.0, 1.0, 1.0, List.of(0.1, 0.2, 0.3), List.of(0.0, 0.0, 0.0, 1.0));
        final QueryEntities.EntityData entityData = new QueryEntities.EntityData(
            UUID.randomUUID(), "minecraft:block_display", 0.0, 0.0, 0.0, null, List.of(), badTransform);
        when(agentClient.queryEntities(eq("world"), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(new QueryEntities.Response(1L, 1, List.of(entityData)));
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            agentClient,
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.snapshotEntities("world", null, null, null))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("4");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void snapshotEntities_shouldThrowNullPointerExceptionWhenWorldNameIsNull()
    {
        // setup
        final DefaultLightkeeperFramework framework = new DefaultLightkeeperFramework(
            runtimeManifest(),
            mock(MinecraftServerProcess.class),
            mock(UdsAgentClient.class),
            new PlayerScopeRegistry()
        );

        // execute + verify
        assertThatThrownBy(() -> framework.snapshotEntities(null, null, null, null))
            .isInstanceOf(NullPointerException.class);
    }

    private static RuntimeManifest runtimeManifest()
    {
        return new RuntimeManifest(
            "paper",
            "1.21.11",
            1L,
            "cache-key",
            "/tmp/lightkeeper/server",
            "/tmp/lightkeeper/server/server.jar",
            1024,
            "/tmp/lightkeeper/agent.sock",
            "auth-token",
            "/tmp/lightkeeper/agent.jar",
            "agent-sha256",
            1,
            "agent-cache-identity",
            null,
            List.of()
        );
    }

    private static RuntimeManifest runtimeManifestWithProvisionedWorlds(
        List<RuntimeManifest.ProvisionedWorld> provisionedWorlds)
    {
        return new RuntimeManifest(
            "paper",
            "1.21.11",
            1L,
            "cache-key",
            "/tmp/lightkeeper/server",
            "/tmp/lightkeeper/server/server.jar",
            1024,
            "/tmp/lightkeeper/agent.sock",
            "auth-token",
            "/tmp/lightkeeper/agent.jar",
            "agent-sha256",
            1,
            "agent-cache-identity",
            null,
            provisionedWorlds
        );
    }
}
