package nl.pim16aap2.lightkeeper.framework;

import java.time.Duration;

/**
 * LightKeeper end-to-end test framework entrypoint.
 *
 * <p>The framework surface is organised into facets, each returned by an accessor: {@link #server()},
 * {@link #worlds()}, {@link #bots()}, and {@link #events()}.
 */
public interface ILightkeeperFramework extends AutoCloseable
{
    /**
     * Gets the server-control facet: command execution, console output, platform, filesystem access, process
     * lifecycle, tick counter, and captured server errors.
     *
     * @return The server-control facet.
     */
    IServerControl server();

    /**
     * Gets the worlds facet: create worlds from defaults, specs, or provisioned templates, or via a builder.
     *
     * @return The worlds facet.
     */
    IWorlds worlds();

    /**
     * Gets the bots facet: join synthetic players into a world, or configure one via a builder.
     *
     * @return The bots facet.
     */
    IBots bots();

    /**
     * Gets the events facet: capture Bukkit events for later inspection.
     *
     * @return The events facet.
     */
    IEvents events();

    /**
     * Waits until a condition is true or timeout expires.
     *
     * @param condition
     *     The condition.
     * @param timeout
     *     Timeout duration.
     */
    void waitUntil(Condition condition, Duration timeout);

    @Override
    void close();
}
