package example;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/** An unrelated unit test deliberately has no LightKeeper extension. */
class OrdinaryUnitTest
{
    @Test
    void ordinaryTest_shouldRunWithoutPreparingMinecraft()
    {
        // setup
        final String value = "ordinary";

        // execute
        final String actual = value.toUpperCase(java.util.Locale.ROOT);

        // verify
        assertThat(actual).isEqualTo("ORDINARY");
    }
}
