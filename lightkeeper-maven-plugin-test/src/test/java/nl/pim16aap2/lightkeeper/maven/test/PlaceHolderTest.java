package nl.pim16aap2.lightkeeper.maven.test;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PlaceHolderTest
{
    @Test
    void constructor_shouldCreateInstance()
    {
        // setup + execute
        final PlaceHolder placeHolder = new PlaceHolder();

        // verify
        assertThat(placeHolder).isNotNull();
    }
}
