package nl.pim16aap2.lightkeeper.framework.internal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkInternalVisibilityTest
{
    @Test
    void iFrameworkGateway_shouldNotBePublic()
    {
        // setup
        final int modifiers = IFrameworkGateway.class.getModifiers();

        // execute
        final boolean isPublic = Modifier.isPublic(modifiers);

        // verify
        assertThat(isPublic).isFalse();
    }
}
