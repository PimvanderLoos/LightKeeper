package nl.pim16aap2.lightkeeper.agent.spigot;

import nl.pim16aap2.lightkeeper.protocol.AgentErrorCode;
import nl.pim16aap2.lightkeeper.protocol.AgentProtocolException;
import nl.pim16aap2.lightkeeper.protocol.BlockType;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import nl.pim16aap2.lightkeeper.protocol.ExecuteCommand;
import nl.pim16aap2.lightkeeper.protocol.GetServerTick;
import nl.pim16aap2.lightkeeper.protocol.IsChunkLoaded;
import nl.pim16aap2.lightkeeper.protocol.LoadChunk;
import nl.pim16aap2.lightkeeper.protocol.NewWorld;
import nl.pim16aap2.lightkeeper.protocol.QueryEntities;
import nl.pim16aap2.lightkeeper.protocol.SetBlock;
import nl.pim16aap2.lightkeeper.protocol.UnloadChunk;
import nl.pim16aap2.lightkeeper.protocol.WaitTicks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentWorldActionsTest
{
    @Test
    void incrementTick_shouldIncreaseTickCounter()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(5L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        worldActions.incrementTick();

        // verify
        assertThat(tickCounter.get()).isEqualTo(6L);
    }

    @Test
    void handleGetServerTick_shouldReturnCurrentTickValue()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(17L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        final GetServerTick.Response response = worldActions.handleGetServerTick(new GetServerTick.Command("request-1"));

        // verify
        assertThat(response.tick()).isEqualTo(17L);
    }

    @Test
    void handleWaitTicks_shouldThrowWhenTicksAreNegative()
    {
        // setup + execute + verify
        assertThatThrownBy(() -> new WaitTicks.Command("request-2", -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ticks");
    }

    @Test
    void handleWaitTicks_shouldReturnStartAndEndTickWhenTicksAreZero()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(9L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);

        // execute
        final WaitTicks.Response response = worldActions.handleWaitTicks(new WaitTicks.Command("request-3", 0));

        // verify
        assertThat(response.startTick()).isEqualTo(9L);
        assertThat(response.endTick()).isEqualTo(9L);
    }

    @Test
    void handleLoadChunk_shouldReturnWorldLoadResult()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.loadChunk(2, 3, true)).thenReturn(true);

        // execute
        final LoadChunk.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleLoadChunk(new LoadChunk.Command("request-load", "world", 2, 3));
        }

        // verify
        assertThat(response.loaded()).isTrue();
        verify(world).loadChunk(2, 3, true);
    }

    @Test
    void handleLoadChunk_shouldReturnFalseWhenLoadFails()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.loadChunk(2, 3, true)).thenReturn(false);

        // execute
        final LoadChunk.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleLoadChunk(new LoadChunk.Command("request-load", "world", 2, 3));
        }

        // verify
        assertThat(response.loaded()).isFalse();
    }

    @Test
    void handleUnloadChunk_shouldReturnWorldUnloadResult()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.unloadChunk(4, 5)).thenReturn(false);

        // execute
        final UnloadChunk.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleUnloadChunk(new UnloadChunk.Command("request-unload", "world", 4, 5));
        }

        // verify
        assertThat(response.unloaded()).isFalse();
    }

    @Test
    void handleIsChunkLoaded_shouldReturnWorldLoadedResult()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.isChunkLoaded(6, 7)).thenReturn(true);

        // execute
        final IsChunkLoaded.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleIsChunkLoaded(new IsChunkLoaded.Command("request-loaded", "world", 6, 7));
        }

        // verify
        assertThat(response.loaded()).isTrue();
    }

    @Test
    void handleQueryEntities_shouldReturnAllEntitiesWhenUnbounded()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong(42L));
        final World world = mock();
        final UUID entityId = UUID.randomUUID();
        final Entity entity = mockZombieEntity(entityId, 1.0, 2.0, 3.0, null, Set.of());
        when(world.getEntities()).thenReturn(List.of(entity));
        final QueryEntities.Command command =
            new QueryEntities.Command("request-qe-1", "world", null, false, 0, 0, 0, 0, 0, 0, false);

        // execute
        final QueryEntities.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleQueryEntities(command);
        }

        // verify
        assertThat(response.tick()).isEqualTo(42L);
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.entities()).singleElement().satisfies(data ->
        {
            assertThat(data.uuid()).isEqualTo(entityId);
            assertThat(data.typeKey()).isEqualTo("minecraft:zombie");
            assertThat(data.x()).isEqualTo(1.0);
            assertThat(data.y()).isEqualTo(2.0);
            assertThat(data.z()).isEqualTo(3.0);
        });
    }

    @Test
    void handleQueryEntities_shouldQueryNearbyEntitiesWhenBounded()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final Entity entity = mockZombieEntity(UUID.randomUUID(), 1.0, 2.0, 3.0, null, Set.of());
        final BoundingBox expectedBounds = new BoundingBox(0, 1, 2, 11.0, 12.0, 13.0);
        when(world.getNearbyEntities(expectedBounds)).thenReturn(List.of(entity));
        final QueryEntities.Command command =
            new QueryEntities.Command("request-qe-2", "world", null, true, 0, 1, 2, 10, 11, 12, false);

        // execute
        final QueryEntities.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleQueryEntities(command);
        }

        // verify
        assertThat(response.count()).isEqualTo(1);
        verify(world).getNearbyEntities(expectedBounds);
    }

    @Test
    void handleQueryEntities_shouldMatchTypeFilterGivenUppercaseTypeKeyWithoutNamespace()
        throws Exception
    {
        // setup + execute + verify
        assertTypeFilterMatchesOnlyZombie("ZOMBIE");
    }

    @Test
    void handleQueryEntities_shouldMatchTypeFilterGivenNamespacedTypeKeyWithWhitespace()
        throws Exception
    {
        // setup + execute + verify
        assertTypeFilterMatchesOnlyZombie(" minecraft:zombie ");
    }

    @Test
    void handleQueryEntities_shouldMatchTypeFilterGivenLowercaseTypeKeyWithoutNamespace()
        throws Exception
    {
        // setup + execute + verify
        assertTypeFilterMatchesOnlyZombie("zombie");
    }

    private void assertTypeFilterMatchesOnlyZombie(String entityTypeKey)
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final UUID zombieId = UUID.randomUUID();
        final Entity zombie = mockZombieEntity(zombieId, 0.0, 0.0, 0.0, null, Set.of());
        final Entity creeper = mock();
        when(creeper.getType()).thenReturn(EntityType.CREEPER);
        when(world.getEntities()).thenReturn(List.of(zombie, creeper));
        final QueryEntities.Command command =
            new QueryEntities.Command("request-qe-filter", "world", entityTypeKey, false, 0, 0, 0, 0, 0, 0, false);

        // execute
        final QueryEntities.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleQueryEntities(command);
        }

        // verify
        assertThat(response.count()).isEqualTo(1);
        assertThat(response.entities()).singleElement()
            .extracting(QueryEntities.EntityData::uuid).isEqualTo(zombieId);
    }

    @Test
    void handleQueryEntities_shouldReturnCountOnlyResultsWhenCountOnlyIsTrue()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        when(world.getEntities()).thenReturn(List.of(mock(Entity.class), mock(Entity.class)));
        final QueryEntities.Command command =
            new QueryEntities.Command("request-qe-count", "world", null, false, 0, 0, 0, 0, 0, 0, true);

        // execute
        final QueryEntities.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleQueryEntities(command);
        }

        // verify
        assertThat(response.count()).isEqualTo(2);
        assertThat(response.entities()).isEmpty();
    }

    @Test
    void handleQueryEntities_shouldMapEntityDataFieldsAndReturnNullTransformForNonDisplayEntity()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final UUID entityId = UUID.randomUUID();
        final Set<NamespacedKey> pdcKeys = Set.of(
            new NamespacedKey("custom", "zeta"), new NamespacedKey("custom", "alpha"));
        final Entity entity = mockZombieEntity(entityId, 1.5, 64.0, -2.75, "Bob", pdcKeys);
        when(world.getEntities()).thenReturn(List.of(entity));
        final QueryEntities.Command command =
            new QueryEntities.Command("request-qe-map", "world", null, false, 0, 0, 0, 0, 0, 0, false);

        // execute
        final QueryEntities.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleQueryEntities(command);
        }

        // verify
        assertThat(response.entities()).singleElement().satisfies(data ->
        {
            assertThat(data.uuid()).isEqualTo(entityId);
            assertThat(data.x()).isEqualTo(1.5);
            assertThat(data.y()).isEqualTo(64.0);
            assertThat(data.z()).isEqualTo(-2.75);
            assertThat(data.customName()).isEqualTo("Bob");
            assertThat(data.pdcKeys()).containsExactly("custom:alpha", "custom:zeta");
            assertThat(data.transform()).isNull();
        });
    }

    @Test
    void handleQueryEntities_shouldMapTransformDataForDisplayEntity()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final Display display = mock();
        when(display.getUniqueId()).thenReturn(UUID.randomUUID());
        when(display.getType()).thenReturn(EntityType.BLOCK_DISPLAY);
        when(display.getLocation()).thenReturn(new Location(null, 0.0, 0.0, 0.0));
        when(display.getCustomName()).thenReturn(null);
        final PersistentDataContainer pdc = mock();
        when(pdc.getKeys()).thenReturn(Set.of());
        when(display.getPersistentDataContainer()).thenReturn(pdc);
        final Transformation transformation = new Transformation(
            new Vector3f(1.0f, 2.0f, 3.0f),
            new Quaternionf(0.1f, 0.2f, 0.3f, 0.4f),
            new Vector3f(4.0f, 5.0f, 6.0f),
            new Quaternionf(0.5f, 0.6f, 0.7f, 0.8f)
        );
        when(display.getTransformation()).thenReturn(transformation);
        when(world.getEntities()).thenReturn(List.of(display));
        final QueryEntities.Command command =
            new QueryEntities.Command("request-qe-transform", "world", null, false, 0, 0, 0, 0, 0, 0, false);

        // execute
        final QueryEntities.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleQueryEntities(command);
        }

        // verify
        assertThat(response.entities()).singleElement().extracting(QueryEntities.EntityData::transform)
            .satisfies(transform ->
            {
                assertThat(transform).isNotNull();
                assertThat(transform.translationX()).isEqualTo(1.0);
                assertThat(transform.translationY()).isEqualTo(2.0);
                assertThat(transform.translationZ()).isEqualTo(3.0);
                assertThat(transform.scaleX()).isEqualTo(4.0);
                assertThat(transform.scaleY()).isEqualTo(5.0);
                assertThat(transform.scaleZ()).isEqualTo(6.0);
                assertThat(transform.leftRotation()).containsExactly(
                    (double) 0.1f, (double) 0.2f, (double) 0.3f, (double) 0.4f);
                assertThat(transform.rightRotation()).containsExactly(
                    (double) 0.5f, (double) 0.6f, (double) 0.7f, (double) 0.8f);
            });
    }

    private static Entity mockZombieEntity(
        UUID uuid, double x, double y, double z, @Nullable String customName, Set<NamespacedKey> pdcKeys)
    {
        final Entity entity = mock();
        when(entity.getUniqueId()).thenReturn(uuid);
        when(entity.getType()).thenReturn(EntityType.ZOMBIE);
        when(entity.getLocation()).thenReturn(new Location(null, x, y, z));
        when(entity.getCustomName()).thenReturn(customName);
        final PersistentDataContainer pdc = mock();
        when(pdc.getKeys()).thenReturn(pdcKeys);
        when(entity.getPersistentDataContainer()).thenReturn(pdc);
        return entity;
    }

    @Test
    void detectServerPlatform_shouldPreferPaperClassSignal()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "CraftBukkit",
            "git-Spigot-abc",
            "org.bukkit.craftbukkit.CraftServer",
            true
        );

        // verify
        assertThat(platform).isEqualTo("PAPER");
    }

    @Test
    void detectServerPlatform_shouldMapCraftBukkitServerToSpigot()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "CraftBukkit",
            "1.21.11-R0.1-SNAPSHOT",
            "org.bukkit.craftbukkit.CraftServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("SPIGOT");
    }

    @Test
    void detectServerPlatform_shouldReturnUnknownWhenNeitherPaperNorSpigotIdentifiersFound()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "SomeOtherServer",
            "1.21.11-R0.1-SNAPSHOT",
            "com.someother.SomeServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("UNKNOWN");
    }

    @Test
    void detectServerPlatform_shouldReturnPaperWhenBukkitNameContainsPaper()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "Paper",
            "1.21.11-R0.1-SNAPSHOT",
            "com.someserver.SomeServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("PAPER");
    }

    @Test
    void detectServerPlatform_shouldReturnSpigotWhenVersionContainsSpigot()
    {
        // execute
        final String platform = AgentPlatformDetector.detect(
            "CraftBukkit",
            "git-Spigot-1.21.11-R0.1",
            "com.someserver.SomeServer",
            false
        );

        // verify
        assertThat(platform).isEqualTo("SPIGOT");
    }

    @Test
    void handleWaitTicks_shouldReturnInterruptedWhenThreadIsInterrupted()
    {
        // setup
        final AtomicLong tickCounter = new AtomicLong(0L);
        final AgentWorldActions worldActions = createWorldActions(tickCounter);
        final WaitTicks.Command command = new WaitTicks.Command("request-interrupted", 1000);

        // execute + verify
        Thread.currentThread().interrupt();
        try
        {
            assertThatThrownBy(() -> worldActions.handleWaitTicks(command))
                .isInstanceOf(AgentProtocolException.class)
                .extracting(exception -> ((AgentProtocolException) exception).errorCode())
                .isEqualTo(AgentErrorCode.INTERRUPTED);
        }
        finally
        {
            Thread.interrupted();
        }
    }

    @Test
    void executeCommandCommand_shouldRejectBlankCommand()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() ->
            new ExecuteCommand.Command("request-blank", CommandSource.CONSOLE, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("command");
    }

    @Test
    void newWorldCommand_shouldRejectBlankWorldName()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() ->
            new NewWorld.Command("request-blank-world", "   ", "NORMAL", "NORMAL", 0L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("worldName");
    }

    @Test
    void setBlockCommand_shouldRejectBlankMaterial()
    {
        // setup + execute + verify — validation is enforced by the command's compact constructor
        assertThatThrownBy(() ->
            new SetBlock.Command("request-blank-mat", "world", 0, 64, 0, "   ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("material");
    }

    @Test
    void handleSetBlock_shouldThrowWhenMaterialIsUnknown()
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());

        final SetBlock.Command command =
            new SetBlock.Command("request-unknown-mat", "world", 0, 64, 0, "not_a_material", null);

        // execute + verify
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<Material> materialMockedStatic = mockStatic(Material.class))
        {
            materialMockedStatic.when(() -> Material.matchMaterial(anyString(), eq(true))).thenReturn(null);
            assertThatThrownBy(() -> worldActions.handleSetBlock(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown material");
        }
    }

    @Test
    void handleBlockType_shouldReturnMaterialAndBlockDataFromBlock()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final Block block = mock();
        final BlockData blockData = mock();
        when(block.getType()).thenReturn(Material.STONE);
        when(block.getBlockData()).thenReturn(blockData);
        when(blockData.getAsString()).thenReturn("minecraft:stone");
        when(world.getBlockAt(0, 64, 0)).thenReturn(block);

        // execute
        final BlockType.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            response = worldActions.handleBlockType(new BlockType.Command("request-blocktype", "world", 0, 64, 0));
        }

        // verify
        assertThat(response.material()).isEqualTo(Material.STONE.getKey().toString());
        assertThat(response.blockData()).isEqualTo("minecraft:stone");
    }

    @Test
    void handleSetBlock_shouldApplyBlockDataWhenPresent()
        throws Exception
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final Block block = mock();
        final BlockData blockData = mock();
        when(block.getType()).thenReturn(Material.LEVER);
        when(world.getBlockAt(0, 64, 0)).thenReturn(block);
        final SetBlock.Command command = new SetBlock.Command(
            "request-blockdata", "world", 0, 64, 0, "lever", "minecraft:lever[powered=true]");

        // execute
        final SetBlock.Response response;
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkitMockedStatic.when(() -> Bukkit.createBlockData("minecraft:lever[powered=true]"))
                .thenReturn(blockData);
            response = worldActions.handleSetBlock(command);
        }

        // verify
        assertThat(response.material()).isEqualTo("LEVER");
        verify(block).setBlockData(blockData);
        verify(block, never()).setType(any());
    }

    @Test
    void handleSetBlock_shouldPropagateIllegalArgumentExceptionWhenBlockDataIsMalformed()
    {
        // setup
        final AgentWorldActions worldActions = createWorldActions(new AtomicLong());
        final World world = mock();
        final Block block = mock();
        when(world.getBlockAt(0, 64, 0)).thenReturn(block);
        final SetBlock.Command command = new SetBlock.Command(
            "request-malformed", "world", 0, 64, 0, "lever", "not valid block data");

        // execute + verify
        try (MockedStatic<Bukkit> bukkitMockedStatic = mockStatic(Bukkit.class))
        {
            bukkitMockedStatic.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkitMockedStatic.when(() -> Bukkit.getWorld("world")).thenReturn(world);
            bukkitMockedStatic.when(() -> Bukkit.createBlockData("not valid block data"))
                .thenThrow(new IllegalArgumentException("Unknown block data"));

            assertThatThrownBy(() -> worldActions.handleSetBlock(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown block data");
        }
        verify(block, never()).setType(any());
    }

    private static AgentWorldActions createWorldActions(AtomicLong tickCounter)
    {
        final JavaPlugin plugin = mock();
        final AgentMainThreadExecutor mainThreadExecutor = new AgentMainThreadExecutor(plugin);
        return new AgentWorldActions(plugin, mainThreadExecutor, tickCounter);
    }
}
