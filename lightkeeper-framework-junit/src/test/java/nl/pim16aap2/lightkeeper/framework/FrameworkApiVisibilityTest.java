package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class FrameworkApiVisibilityTest
{
    @Test
    void constructors_shouldNotBePublicForHandleTypes() throws Exception
    {
        // setup
        final Constructor<PlayerHandle> playerConstructor =
            PlayerHandle.class.getDeclaredConstructor(FrameworkGateway.class, UUID.class, String.class);
        final Constructor<MenuHandle> menuConstructor =
            MenuHandle.class.getDeclaredConstructor(FrameworkGateway.class, PlayerHandle.class);
        final Constructor<WorldHandle> worldConstructor =
            WorldHandle.class.getDeclaredConstructor(FrameworkGateway.class, String.class);

        // execute
        final boolean isPlayerConstructorPublic = Modifier.isPublic(playerConstructor.getModifiers());
        final boolean isMenuConstructorPublic = Modifier.isPublic(menuConstructor.getModifiers());
        final boolean isWorldConstructorPublic = Modifier.isPublic(worldConstructor.getModifiers());

        // verify
        assertThat(isPlayerConstructorPublic).isFalse();
        assertThat(isMenuConstructorPublic).isFalse();
        assertThat(isWorldConstructorPublic).isFalse();
    }
}
