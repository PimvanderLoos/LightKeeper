package nl.pim16aap2.lightkeeper.framework;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

/**
 * Handle for a synthetic player.
 */
public final class PlayerHandle
{
    private static final Duration DEFAULT_MENU_WAIT_TIMEOUT = Duration.ofSeconds(10);

    private final LightkeeperFramework framework;
    private final UUID uniqueId;
    private final String name;

    public PlayerHandle(LightkeeperFramework framework, UUID uniqueId, String name)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
        this.uniqueId = Objects.requireNonNull(uniqueId, "uniqueId may not be null.");
        this.name = Objects.requireNonNull(name, "name may not be null.");
    }

    public UUID uniqueId()
    {
        return uniqueId;
    }

    public String name()
    {
        return name;
    }

    public PlayerHandle executeCommand(String command)
    {
        framework.executePlayerCommand(this, command);
        return this;
    }

    public PlayerHandle placeBlock(String materialKey, int x, int y, int z)
    {
        framework.placePlayerBlock(this, materialKey, x, y, z);
        return this;
    }

    public PlayerHandle andWaitTicks(int ticks)
    {
        framework.waitTicks(ticks);
        return this;
    }

    public MenuHandle andWaitForMenuOpen(int timeoutSeconds)
    {
        final MenuHandle menuHandle = new MenuHandle(framework, this);
        framework.waitUntil(
            () -> menuHandle.snapshot().open(),
            Duration.ofSeconds(timeoutSeconds)
        );
        return menuHandle;
    }

    public MenuHandle andWaitForMenuOpen()
    {
        final MenuHandle menuHandle = new MenuHandle(framework, this);
        framework.waitUntil(
            () -> menuHandle.snapshot().open(),
            DEFAULT_MENU_WAIT_TIMEOUT
        );
        return menuHandle;
    }

    public MenuHandle getMenu()
    {
        final MenuHandle menuHandle = new MenuHandle(framework, this);
        return menuHandle.snapshot().open() ? menuHandle : null;
    }

    public void remove()
    {
        framework.removePlayer(this);
    }
}
