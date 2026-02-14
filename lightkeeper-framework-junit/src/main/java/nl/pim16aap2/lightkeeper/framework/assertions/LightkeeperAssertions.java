package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.Assertions;

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
    public static WorldHandleAssert assertWorld(WorldHandle actual)
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
    public static MenuHandleAssert assertMenu(MenuHandle actual)
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
    public static PlayerHandleAssert assertPlayer(PlayerHandle actual)
    {
        return new PlayerHandleAssert(actual);
    }
}
