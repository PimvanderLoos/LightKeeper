package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.FreshServer;
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
class LightkeeperMixedFreshLifecycleIT
{
    private @Nullable ILightkeeperFramework firstSharedFramework;
    private @Nullable ILightkeeperFramework freshFramework;
    private @Nullable ILightkeeperFramework secondSharedFramework;

    @Test
    @Order(1)
    void frameworkLifecycle_shouldUseSharedFrameworkForFirstUnannotatedMethod(ILightkeeperFramework framework)
    {
        // setup
        firstSharedFramework = framework;

        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(mainWorld.name()).isNotBlank();
    }

    @Test
    @Order(2)
    void frameworkLifecycle_shouldReuseSharedFrameworkForSecondUnannotatedMethod(ILightkeeperFramework framework)
    {
        // setup
        assertThat(firstSharedFramework).isNotNull();

        // execute
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(framework).isSameAs(firstSharedFramework);
        assertThat(mainWorld.name()).isNotBlank();
    }

    @Test
    @Order(3)
    @FreshServer
    void frameworkLifecycle_shouldUseFreshFrameworkForAnnotatedMethod(ILightkeeperFramework framework)
    {
        // setup
        assertThat(firstSharedFramework).isNotNull();

        // execute
        freshFramework = framework;
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(framework).isNotSameAs(firstSharedFramework);
        assertThat(mainWorld.name()).isNotBlank();
    }

    @Test
    @Order(4)
    void frameworkLifecycle_shouldCreateNewSharedFrameworkAfterAnnotatedMethod(ILightkeeperFramework framework)
    {
        // setup
        assertThat(firstSharedFramework).isNotNull();
        assertThat(freshFramework).isNotNull();

        // execute
        secondSharedFramework = framework;
        final var mainWorld = framework.mainWorld();

        // verify
        assertThat(secondSharedFramework).isNotNull();
        assertThat(framework).isNotSameAs(firstSharedFramework);
        assertThat(framework).isNotSameAs(freshFramework);
        assertThat(mainWorld.name()).isNotBlank();
    }
}
