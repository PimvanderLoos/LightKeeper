package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.CommandResult;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.Vec3;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.protocol.CommandSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperEntityQueryIT
{
    private static final String BLOCK_DISPLAY = "minecraft:block_display";

    @Test
    void entities_shouldCountAndSnapshotSummonedDisplayEntity(ILightkeeperFramework framework)
    {
        // setup — a fresh world isolates the query from other tests; natural mob spawning is not suppressed,
        // so every query filters on the display type only this test summons. All transform components are
        // binary-exact floats so the float-to-double comparison below is exact. The target chunk must be
        // force-loaded: a playerless world unloads its chunks (and their entities) right after the summon
        // command's temporary chunk ticket expires, and entity queries only see loaded chunks.
        final WorldHandle world = framework.worlds().create(new WorldSpec(
            "lk_entityquery_" + UUID.randomUUID().toString().replace("-", ""),
            WorldSpec.WorldType.FLAT,
            WorldSpec.WorldEnvironment.NORMAL,
            99L
        ));
        final CommandResult forceloadResult = framework.server().executeCommand(
            CommandSource.CONSOLE,
            "execute in minecraft:%s run forceload add 8 8".formatted(world.name()));
        assertThat(forceloadResult.success()).isTrue();
        final String summonCommand = (
            "execute in minecraft:%s run summon minecraft:block_display 8.5 100.0 8.5 "
                + "{CustomName:\"lk-display\",block_state:{Name:\"minecraft:stone\"},transformation:{"
                + "translation:[0.25f,0.5f,0.75f],scale:[2.0f,3.0f,4.0f],"
                + "left_rotation:[0.0f,0.0f,0.0f,1.0f],right_rotation:[0.0f,1.0f,0.0f,0.0f]}}"
        ).formatted(world.name());

        // execute
        final CommandResult summonResult = framework.server().executeCommand(CommandSource.CONSOLE, summonCommand);

        // verify
        assertThat(summonResult.success()).isTrue();
        eventually(Duration.ofSeconds(10), () ->
            assertThat(world.entities().ofType(BLOCK_DISPLAY).count()).isEqualTo(1));

        final List<EntitySnapshot> snapshots = world.entities().ofType(BLOCK_DISPLAY).snapshot();
        assertThat(snapshots).hasSize(1);
        final EntitySnapshot snapshot = snapshots.getFirst();
        assertThat(snapshot.uuid()).isNotNull();
        assertThat(snapshot.typeKey()).isEqualTo(BLOCK_DISPLAY);
        assertThat(snapshot.position()).isEqualTo(new Vec3(8.5, 100.0, 8.5));
        assertThat(snapshot.customName()).isEqualTo("lk-display");
        assertThat(snapshot.pdcKeys()).isEmpty();
        assertThat(snapshot.tick()).isPositive();
        assertThat(framework.server().currentTick()).isGreaterThanOrEqualTo(snapshot.tick());

        final EntitySnapshot.Transform transform = snapshot.transform();
        assertThat(transform).isNotNull();
        assertThat(transform.translation()).isEqualTo(new Vec3(0.25, 0.5, 0.75));
        assertThat(transform.scale()).isEqualTo(new Vec3(2.0, 3.0, 4.0));
        assertThat(transform.leftRotation()).isEqualTo(new EntitySnapshot.Rotation(0.0, 0.0, 0.0, 1.0));
        assertThat(transform.rightRotation()).isEqualTo(new EntitySnapshot.Rotation(0.0, 1.0, 0.0, 0.0));

        // Spatial bounds: the display sits inside the near box and outside the far box.
        final var boundedNear = world.entities()
            .ofType(BLOCK_DISPLAY)
            .within(new BlockPos(0, 90, 0), new BlockPos(16, 110, 16));
        final var boundedFar = world.entities()
            .ofType(BLOCK_DISPLAY)
            .within(new BlockPos(100, 90, 100), new BlockPos(116, 110, 116));
        assertThat(boundedNear.count()).isEqualTo(1);
        assertThat(boundedFar.count()).isZero();

        // Type filtering: no armor stands were summoned, so the same box must be empty for that type.
        assertThat(world.entities()
            .ofType("minecraft:armor_stand")
            .within(new BlockPos(0, 90, 0), new BlockPos(16, 110, 16))
            .count())
            .isZero();
    }

    @Test
    void snapshot_shouldReturnNullTransformForNonDisplayEntity(ILightkeeperFramework framework)
    {
        // setup — armor stands are regular (non-display) entities: Marker:1b and NoGravity:1b pin it in
        // place so the position assertion is exact. The chunk is force-loaded for the same reason as above:
        // playerless worlds unload chunks (and their entities) otherwise.
        final WorldHandle world = framework.worlds().create(new WorldSpec(
            "lk_entityquery_" + UUID.randomUUID().toString().replace("-", ""),
            WorldSpec.WorldType.FLAT,
            WorldSpec.WorldEnvironment.NORMAL,
            99L
        ));
        final CommandResult forceloadResult = framework.server().executeCommand(
            CommandSource.CONSOLE,
            "execute in minecraft:%s run forceload add 4 4".formatted(world.name()));
        assertThat(forceloadResult.success()).isTrue();
        final String summonCommand =
            "execute in minecraft:%s run summon minecraft:armor_stand 4.5 100.0 4.5 {Marker:1b,NoGravity:1b}"
                .formatted(world.name());

        // execute
        final CommandResult summonResult = framework.server().executeCommand(CommandSource.CONSOLE, summonCommand);

        // verify
        assertThat(summonResult.success()).isTrue();
        eventually(Duration.ofSeconds(10), () ->
            assertThat(world.entities().ofType("minecraft:armor_stand").count()).isEqualTo(1));

        final List<EntitySnapshot> snapshots = world.entities().ofType("minecraft:armor_stand").snapshot();
        assertThat(snapshots).hasSize(1);
        final EntitySnapshot snapshot = snapshots.getFirst();
        assertThat(snapshot.typeKey()).isEqualTo("minecraft:armor_stand");
        assertThat(snapshot.position()).isEqualTo(new Vec3(4.5, 100.0, 4.5));
        assertThat(snapshot.customName()).isNull();
        assertThat(snapshot.transform()).isNull();
    }
}
