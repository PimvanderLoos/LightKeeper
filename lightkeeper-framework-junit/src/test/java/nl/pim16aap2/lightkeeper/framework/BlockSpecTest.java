package nl.pim16aap2.lightkeeper.framework;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BlockSpecTest
{
    @Test
    void parse_shouldReturnMaterialOnlySpecWhenNoBracketsPresent()
    {
        // execute
        final BlockSpec spec = BlockSpec.parse("minecraft:stone");

        // verify
        assertThat(spec.materialKey()).isEqualTo("minecraft:stone");
        assertThat(spec.properties()).isEmpty();
    }

    @Test
    void parse_shouldNormalizeUnprefixedMaterialKey()
    {
        // execute
        final BlockSpec spec = BlockSpec.parse("STONE");

        // verify
        assertThat(spec.materialKey()).isEqualTo("minecraft:stone");
    }

    @Test
    void parse_shouldParsePropertiesPreservingInsertionOrder()
    {
        // execute
        final BlockSpec spec = BlockSpec.parse("minecraft:lever[powered=true,facing=north]");

        // verify
        assertThat(spec.materialKey()).isEqualTo("minecraft:lever");
        assertThat(spec.properties().keySet()).containsExactly("powered", "facing");
        assertThat(spec.properties()).containsEntry("powered", "true").containsEntry("facing", "north");
    }

    @Test
    void parse_shouldThrowIllegalArgumentExceptionWhenClosingBracketIsMissing()
    {
        // execute + verify
        assertThatThrownBy(() -> BlockSpec.parse("minecraft:lever[powered=true"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("missing closing bracket");
    }

    @Test
    void parse_shouldThrowIllegalArgumentExceptionWhenPropertyHasNoEqualsSign()
    {
        // execute + verify
        assertThatThrownBy(() -> BlockSpec.parse("minecraft:lever[powered]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a key=value pair");
    }

    @Test
    void parse_shouldThrowIllegalArgumentExceptionWhenPropertyHasBlankKey()
    {
        // execute + verify
        assertThatThrownBy(() -> BlockSpec.parse("minecraft:lever[=true]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a key=value pair");
    }

    @Test
    void parse_shouldThrowIllegalArgumentExceptionWhenPropertyHasTrailingEquals()
    {
        // execute + verify
        assertThatThrownBy(() -> BlockSpec.parse("minecraft:lever[powered=]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a key=value pair");
    }

    @Test
    void parse_shouldThrowIllegalArgumentExceptionWhenPropertyIsDuplicated()
    {
        // execute + verify
        assertThatThrownBy(() -> BlockSpec.parse("minecraft:lever[powered=true,powered=false]"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("duplicate property 'powered'");
    }

    @Test
    void of_shouldCreateMaterialOnlySpecFromBukkitMaterial()
    {
        // execute
        final BlockSpec spec = BlockSpec.of(Material.STONE);

        // verify
        assertThat(spec.materialKey()).isEqualTo("minecraft:stone");
        assertThat(spec.properties()).isEmpty();
    }

    @Test
    void of_shouldCreateMaterialOnlySpecFromMaterialKeyString()
    {
        // execute
        final BlockSpec spec = BlockSpec.of("DIRT");

        // verify
        assertThat(spec.materialKey()).isEqualTo("minecraft:dirt");
        assertThat(spec.properties()).isEmpty();
    }

    @Test
    void with_shouldReturnNewSpecLeavingOriginalUnchanged()
    {
        // setup
        final BlockSpec original = BlockSpec.of(Material.LEVER);

        // execute
        final BlockSpec extended = original.with("powered", "true");

        // verify
        assertThat(original.properties()).isEmpty();
        assertThat(extended.properties()).containsExactly(Map.entry("powered", "true"));
    }

    @Test
    void asString_shouldRoundTripThroughParse()
    {
        // setup
        final String blockData = "minecraft:lever[powered=true,facing=north]";

        // execute
        final String result = BlockSpec.parse(blockData).asString();

        // verify
        assertThat(result).isEqualTo(blockData);
    }

    @Test
    void asString_shouldReturnMaterialKeyOnlyWhenNoPropertiesAreNamed()
    {
        // execute
        final String result = BlockSpec.of(Material.STONE).asString();

        // verify
        assertThat(result).isEqualTo("minecraft:stone");
    }

    @Test
    void constructor_shouldPreserveInsertionOrderFromSuppliedMap()
    {
        // setup
        final Map<String, String> properties = new LinkedHashMap<>();
        properties.put("facing", "north");
        properties.put("powered", "true");

        // execute
        final BlockSpec spec = new BlockSpec("minecraft:lever", properties);

        // verify
        assertThat(spec.properties().keySet()).containsExactly("facing", "powered");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionWhenPropertyNameIsBlank()
    {
        // execute + verify
        assertThatThrownBy(() -> new BlockSpec("minecraft:lever", Map.of(" ", "true")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Property names may not be blank");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionWhenPropertyValueIsBlank()
    {
        // execute + verify
        assertThatThrownBy(() -> new BlockSpec("minecraft:lever", Map.of("powered", " ")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("may not have a blank value");
    }

    @Test
    void constructor_shouldThrowIllegalArgumentExceptionWhenMaterialKeyIsBlank()
    {
        // execute + verify
        assertThatThrownBy(() -> new BlockSpec("   ", Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void constructor_shouldThrowNullPointerExceptionWhenMaterialKeyIsNull()
    {
        // execute + verify
        assertThatThrownBy(() -> new BlockSpec(null, Map.of()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void parse_shouldThrowNullPointerExceptionWhenBlockDataIsNull()
    {
        // execute + verify
        assertThatThrownBy(() -> BlockSpec.parse(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("blockData");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void of_shouldThrowNullPointerExceptionWhenMaterialIsNull()
    {
        // execute + verify
        assertThatThrownBy(() -> BlockSpec.of((Material) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("material");
    }
}
