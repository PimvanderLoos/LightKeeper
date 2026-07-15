package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.BlockPos;
import nl.pim16aap2.lightkeeper.framework.BlockRef;
import nl.pim16aap2.lightkeeper.framework.BlockSpec;
import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Duration;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.catchThrowable;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.eventually;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperBlockStateIT
{
    @Test
    void blockState_shouldPlaceAndMatchLeverStateEndToEnd(ILightkeeperFramework framework)
    {
        // setup — a lever is an attachable block: place its supporting block first so the physics update
        // applied by the placement does not pop it off.
        final WorldHandle world = framework.newWorld(new WorldSpec(
            "lk_blockstate_" + UUID.randomUUID().toString().replace("-", ""),
            WorldSpec.WorldType.FLAT,
            WorldSpec.WorldEnvironment.NORMAL,
            99L
        ));
        final BlockPos supportPosition = new BlockPos(4, 70, 4);
        final BlockPos leverPosition = new BlockPos(4, 71, 4);
        world.setBlockAt(supportPosition, "STONE");

        // execute
        world.setBlockAt(leverPosition, BlockSpec.parse("minecraft:lever[face=floor,powered=true]"));

        // verify
        eventually(Duration.ofSeconds(10), () ->
            assertThat(world).hasBlockAt(leverPosition).withState(BlockSpec.of("lever").with("powered", "true")));
        final BlockRef lever = world.blockAt(leverPosition);
        assertThat(lever.material()).isEqualTo("minecraft:lever");
        assertThat(lever.state().property("face")).isEqualTo("floor");
        assertThat(lever.state().blockData()).contains("powered=true");
        assertThat(lever.is(BlockSpec.parse("minecraft:lever[powered=true]"))).isTrue();
        assertThat(lever.is(BlockSpec.parse("minecraft:lever[powered=false]"))).isFalse();
    }

    @Test
    void setBlockAt_shouldRejectMalformedBlockDataWithTypedError(ILightkeeperFramework framework)
    {
        // setup — the material key parses client-side; the agent's block-data parser is the authority that
        // rejects it, surfacing as a typed INVALID_ARGUMENT error.
        final WorldHandle world = framework.mainWorld();
        final BlockSpec bogusSpec = BlockSpec.parse("minecraft:definitely_not_a_block[foo=bar]");

        // execute
        final Throwable thrown =
            catchThrowable(() -> world.setBlockAt(new BlockPos(0, 200, 0), bogusSpec));

        // verify
        assertThat(thrown)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("INVALID_ARGUMENT");
    }
}
