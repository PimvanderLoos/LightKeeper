package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.framework.internal.IFrameworkGateway;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handle for a synthetic player.
 * <p>
 * This type exposes common player actions used in end-to-end tests and supports fluent method chaining for
 * orchestration-style tests.
 */
public final class PlayerHandle
{
    private static final Duration DEFAULT_MENU_WAIT_TIMEOUT = Duration.ofSeconds(10);

    private final IFrameworkGateway frameworkGateway;
    private final UUID uniqueId;
    private final String name;

    /**
     * Creates a player handle.
     *
     * @param frameworkGateway
     *     Internal gateway for operations.
     * @param uniqueId
     *     Player UUID.
     * @param name
     *     Player name.
     */
    public PlayerHandle(IFrameworkGateway frameworkGateway, UUID uniqueId, String name)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId may not be null.");
        this.name = Objects.requireNonNull(name, "name may not be null.");
    }

    /**
     * Gets the synthetic player UUID.
     *
     * @return Player UUID.
     */
    public UUID uniqueId()
    {
        return uniqueId;
    }

    /**
     * Gets the current player name.
     *
     * @return The player name.
     */
    public String name()
    {
        return name;
    }

    /**
     * Executes a command as this player.
     *
     * @param command
     *     Command text. Leading slash is optional.
     * @return This handle for fluent chaining.
     */
    public PlayerHandle executeCommand(String command)
    {
        frameworkGateway.executePlayerCommand(uniqueId, command);
        return this;
    }

    /**
     * Places a block from this player perspective.
     *
     * @param materialKey
     *     Material key, for example {@code minecraft:stone} or {@code STONE}.
     * @param x
     *     X coordinate.
     * @param y
     *     Y coordinate.
     * @param z
     *     Z coordinate.
     * @return This handle for fluent chaining.
     */
    public PlayerHandle placeBlock(String materialKey, int x, int y, int z)
    {
        frameworkGateway.placePlayerBlock(uniqueId, materialKey, x, y, z);
        return this;
    }

    /**
     * Waits for at least the requested number of server ticks.
     *
     * @param ticks
     *     Tick count to wait.
     * @return This handle for fluent chaining.
     */
    public PlayerHandle andWaitTicks(int ticks)
    {
        frameworkGateway.waitTicks(ticks);
        return this;
    }

    /**
     * Waits for an open menu for this player.
     *
     * @param timeoutSeconds
     *     Timeout in seconds.
     * @return A menu handle bound to this player.
     */
    public MenuHandle andWaitForMenuOpen(int timeoutSeconds)
    {
        final MenuHandle menuHandle = new MenuHandle(frameworkGateway, this);
        frameworkGateway.waitUntil(
            () -> menuHandle.snapshot().open(),
            Duration.ofSeconds(timeoutSeconds)
        );
        return menuHandle;
    }

    /**
     * Waits for an open menu using the default timeout.
     *
     * @return A menu handle bound to this player.
     */
    public MenuHandle andWaitForMenuOpen()
    {
        final MenuHandle menuHandle = new MenuHandle(frameworkGateway, this);
        frameworkGateway.waitUntil(
            () -> menuHandle.snapshot().open(),
            DEFAULT_MENU_WAIT_TIMEOUT
        );
        return menuHandle;
    }

    /**
     * Returns the currently open menu if available.
     *
     * @return The open menu handle, or {@code null} when no actionable menu is open.
     */
    public @Nullable MenuHandle getMenu()
    {
        final MenuHandle menuHandle = new MenuHandle(frameworkGateway, this);
        return menuHandle.snapshot().open() ? menuHandle : null;
    }

    /**
     * Gets all recorded messages received by this player.
     *
     * @return Snapshot of received messages, ordered oldest-to-newest.
     */
    public List<String> receivedMessages()
    {
        return frameworkGateway.playerMessages(uniqueId);
    }

    /**
     * Gets all recorded messages flattened into a single multi-line string.
     *
     * @return Received messages joined with line separators.
     */
    public String receivedMessagesText()
    {
        return String.join(System.lineSeparator(), receivedMessages());
    }

    /**
     * Removes this synthetic player from the server.
     */
    public void remove()
    {
        frameworkGateway.removePlayer(this);
    }
}
