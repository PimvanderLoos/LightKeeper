package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(LightkeeperExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LightkeeperSharedLifecycleIT
{
    private @Nullable ILightkeeperFramework initialFramework;

    @Test
    @Order(1)
    void frameworkLifecycle_shouldProvideSharedFrameworkOnFirstMethod(ILightkeeperFramework framework)
    {
        // setup
        initialFramework = framework;

        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(mainWorld.name()).isNotBlank();
    }

    @Test
    @Order(2)
    void frameworkLifecycle_shouldReuseSharedFrameworkOnSecondMethod(ILightkeeperFramework framework)
    {
        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(initialFramework).isNotNull();
        assertThat(framework).isSameAs(initialFramework);
        assertThat(mainWorld.name()).isNotBlank();
    }
}
