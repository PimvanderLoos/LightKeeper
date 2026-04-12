package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.Assertions;
import org.jspecify.annotations.Nullable;

/**
 * AssertJ entrypoints for LightKeeper handles.
 */
public final class LightkeeperAssertions extends Assertions
{
    private LightkeeperAssertions()
    {
    }

    /**
     * Creates an assertion chain for world-related state.
     *
     * @param actual
     *     World handle under test.
     * @return World assertion entrypoint.
     */
    public static WorldHandleAssert assertThat(@Nullable WorldHandle actual)
    {
        return new WorldHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for menu-related state.
     *
     * @param actual
     *     Menu handle under test.
     * @return Menu assertion entrypoint.
     */
    public static MenuHandleAssert assertThat(@Nullable MenuHandle actual)
    {
        return new MenuHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for player-related state.
     *
     * @param actual
     *     Player handle under test.
     * @return Player assertion entrypoint.
     */
    public static PlayerHandleAssert assertThat(@Nullable PlayerHandle actual)
    {
        return new PlayerHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for framework-level runtime state.
     *
     * @param actual
     *     Framework under test.
     * @return Framework assertion entrypoint.
     */
    public static LightkeeperFrameworkAssert assertThat(@Nullable ILightkeeperFramework actual)
    {
        return new LightkeeperFrameworkAssert(actual);
    }
}
