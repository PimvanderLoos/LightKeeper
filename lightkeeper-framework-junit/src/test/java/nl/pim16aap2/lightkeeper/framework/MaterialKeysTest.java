package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterialKeysTest
{
    @Test
    void normalize_shouldLowercaseAndAddMinecraftPrefixWhenMissing()
    {
        // execute
        final String result = MaterialKeys.normalize("STONE");

        // verify
        assertThat(result).isEqualTo("minecraft:stone");
    }

    @Test
    void normalize_shouldTrimSurroundingWhitespace()
    {
        // execute
        final String result = MaterialKeys.normalize("  stone  ");

        // verify
        assertThat(result).isEqualTo("minecraft:stone");
    }

    @Test
    void normalize_shouldBeIdempotentWhenPrefixAlreadyPresent()
    {
        // execute
        final String result = MaterialKeys.normalize("minecraft:stone");

        // verify
        assertThat(result).isEqualTo("minecraft:stone");
    }

    @Test
    void normalize_shouldLowercaseAnAlreadyPrefixedKey()
    {
        // execute
        final String result = MaterialKeys.normalize("MINECRAFT:STONE");

        // verify
        assertThat(result).isEqualTo("minecraft:stone");
    }

    @Test
    void normalize_shouldThrowIllegalArgumentExceptionWhenBlank()
    {
        // execute + verify
        assertThatThrownBy(() -> MaterialKeys.normalize("   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("blank");
    }

    @Test
    @SuppressWarnings("NullAway") // Intentionally crosses the non-null API boundary to verify fail-fast validation.
    void normalize_shouldThrowNullPointerExceptionWhenNull()
    {
        // execute + verify
        assertThatThrownBy(() -> MaterialKeys.normalize(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessageContaining("materialKey");
    }
}
