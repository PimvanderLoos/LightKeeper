package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.framework.internal.FrameworkGateway;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Handle for a synthetic player.
 */
public final class PlayerHandle
{
    private static final Duration DEFAULT_MENU_WAIT_TIMEOUT = Duration.ofSeconds(10);

    private final FrameworkGateway frameworkGateway;
    private final UUID uniqueId;
    private final String name;

    public PlayerHandle(FrameworkGateway frameworkGateway, UUID uniqueId, String name)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
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
        frameworkGateway.executePlayerCommand(uniqueId, command);
        return this;
    }

    public PlayerHandle placeBlock(String materialKey, int x, int y, int z)
    {
        frameworkGateway.placePlayerBlock(uniqueId, materialKey, x, y, z);
        return this;
    }

    public PlayerHandle andWaitTicks(int ticks)
    {
        frameworkGateway.waitTicks(ticks);
        return this;
    }

    public MenuHandle andWaitForMenuOpen(int timeoutSeconds)
    {
        final MenuHandle menuHandle = new MenuHandle(frameworkGateway, this);
        frameworkGateway.waitUntil(
            () -> menuHandle.snapshot().open(),
            Duration.ofSeconds(timeoutSeconds)
        );
        return menuHandle;
    }

    public MenuHandle andWaitForMenuOpen()
    {
        final MenuHandle menuHandle = new MenuHandle(frameworkGateway, this);
        frameworkGateway.waitUntil(
            () -> menuHandle.snapshot().open(),
            DEFAULT_MENU_WAIT_TIMEOUT
        );
        return menuHandle;
    }

    public MenuHandle getMenu()
    {
        final MenuHandle menuHandle = new MenuHandle(frameworkGateway, this);
        return menuHandle.snapshot().open() ? menuHandle : null;
    }

    public List<String> receivedMessages()
    {
        return frameworkGateway.playerMessages(uniqueId);
    }

    public String receivedMessagesText()
    {
        return String.join(System.lineSeparator(), receivedMessages());
    }

    public void remove()
    {
        frameworkGateway.removePlayer(this);
    }
}
