package nl.pim16aap2.lightkeeper.framework;

import java.util.UUID;

/**
 * Internal handle factory used by the framework runtime and builders.
 * <p>
 * This exists to keep handle constructors non-public while preserving framework wiring from internal packages.
 */
public final class FrameworkHandleFactory
{
    private FrameworkHandleFactory()
    {
    }

    /**
     * Creates a world handle.
     */
    public static WorldHandle worldHandle(FrameworkGateway frameworkGateway, String worldName)
    {
        return new WorldHandle(frameworkGateway, worldName);
    }

    /**
     * Creates a player handle.
     */
    public static PlayerHandle playerHandle(FrameworkGateway frameworkGateway, UUID uniqueId, String name)
    {
        return new PlayerHandle(frameworkGateway, uniqueId, name);
    }

    /**
     * Creates a menu handle.
     */
    public static MenuHandle menuHandle(FrameworkGateway frameworkGateway, PlayerHandle playerHandle)
    {
        return new MenuHandle(frameworkGateway, playerHandle);
    }
}
