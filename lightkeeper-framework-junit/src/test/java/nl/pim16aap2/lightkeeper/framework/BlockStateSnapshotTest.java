package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockStateSnapshotTest
{
    @Test
    void fromBlockData_shouldParseMaterialAndPropertiesFromBlockDataString()
    {
        // setup
        final String blockData = "minecraft:lever[face=floor,facing=north,powered=true]";

        // execute
        final BlockStateSnapshot snapshot = BlockStateSnapshot.fromBlockData(blockData);

        // verify
        assertThat(snapshot.materialKey()).isEqualTo("minecraft:lever");
        assertThat(snapshot.blockData()).isEqualTo(blockData);
        assertThat(snapshot.properties())
            .containsEntry("face", "floor")
            .containsEntry("facing", "north")
            .containsEntry("powered", "true");
    }

    @Test
    void fromBlockData_shouldReturnEmptyPropertiesWhenMaterialHasNoState()
    {
        // execute
        final BlockStateSnapshot snapshot = BlockStateSnapshot.fromBlockData("minecraft:stone");

        // verify
        assertThat(snapshot.materialKey()).isEqualTo("minecraft:stone");
        assertThat(snapshot.properties()).isEmpty();
    }

    @Test
    void property_shouldReturnValueWhenPropertyIsPresent()
    {
        // setup
        final BlockStateSnapshot snapshot = BlockStateSnapshot.fromBlockData("minecraft:lever[powered=true]");

        // execute
        final String value = snapshot.property("powered");

        // verify
        assertThat(value).isEqualTo("true");
    }

    @Test
    void property_shouldReturnNullWhenPropertyIsMissing()
    {
        // setup
        final BlockStateSnapshot snapshot = BlockStateSnapshot.fromBlockData("minecraft:lever[powered=true]");

        // execute
        final String value = snapshot.property("facing");

        // verify
        assertThat(value).isNull();
    }

    @Test
    void matches_shouldReturnFalseWhenMaterialDoesNotMatch()
    {
        // setup
        final BlockStateSnapshot snapshot = BlockStateSnapshot.fromBlockData("minecraft:stone");
        final BlockSpec spec = BlockSpec.of("minecraft:dirt");

        // execute
        final boolean result = snapshot.matches(spec);

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void matches_shouldReturnTrueWhenSpecNamesSubsetOfProperties()
    {
        // setup
        final BlockStateSnapshot snapshot =
            BlockStateSnapshot.fromBlockData("minecraft:lever[face=floor,facing=north,powered=true]");
        final BlockSpec spec = BlockSpec.parse("minecraft:lever[powered=true]");

        // execute
        final boolean result = snapshot.matches(spec);

        // verify
        assertThat(result).isTrue();
    }

    @Test
    void matches_shouldReturnFalseWhenNamedPropertyValueDiffers()
    {
        // setup
        final BlockStateSnapshot snapshot =
            BlockStateSnapshot.fromBlockData("minecraft:lever[face=floor,facing=north,powered=false]");
        final BlockSpec spec = BlockSpec.parse("minecraft:lever[powered=true]");

        // execute
        final boolean result = snapshot.matches(spec);

        // verify
        assertThat(result).isFalse();
    }

    @Test
    void matches_shouldReturnTrueWhenSpecNamesNoPropertiesAndMaterialMatches()
    {
        // setup
        final BlockStateSnapshot snapshot =
            BlockStateSnapshot.fromBlockData("minecraft:lever[face=floor,facing=north,powered=true]");
        final BlockSpec spec = BlockSpec.of("minecraft:lever");

        // execute
        final boolean result = snapshot.matches(spec);

        // verify
        assertThat(result).isTrue();
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify default-on-null.
    void constructor_shouldDefaultPropertiesToEmptyMapWhenNull()
    {
        // execute
        final BlockStateSnapshot snapshot = new BlockStateSnapshot("minecraft:stone", "minecraft:stone", null);

        // verify
        assertThat(snapshot.properties()).isEmpty();
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void constructor_shouldThrowNullPointerExceptionWhenBlockDataIsNull()
    {
        // execute + verify
        assertThatThrownBy(() -> new BlockStateSnapshot("minecraft:stone", null, Map.of()))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("blockData");
    }
}
