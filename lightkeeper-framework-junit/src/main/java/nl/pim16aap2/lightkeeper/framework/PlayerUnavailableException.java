package nl.pim16aap2.lightkeeper.framework;

import java.util.Objects;

/**
 * Thrown when a synthetic player cannot perform an action because its server-side lifecycle state is no longer
 * active.
 *
 * <p>The {@link Reason} is stable for programmatic assertions; the exception message retains the agent's
 * contextual diagnostics, such as the attempted action and recorded death details.
 */
public class PlayerUnavailableException extends IllegalStateException
{
    private static final long serialVersionUID = 1L;

    private final Reason reason;

    /**
     * Creates a player-availability exception.
     *
     * @param reason
     *     Machine-readable reason the player is unavailable.
     * @param message
     *     Human-readable lifecycle and action diagnostics from the agent.
     */
    public PlayerUnavailableException(Reason reason, String message)
    {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason may not be null.");
    }

    /**
     * Returns why the synthetic player was unavailable.
     *
     * @return Machine-readable availability reason.
     */
    public Reason reason()
    {
        return reason;
    }

    /**
     * Machine-readable synthetic-player availability failures.
     */
    public enum Reason
    {
        /** The player handle does not identify a registered synthetic player. */
        NOT_REGISTERED,
        /** The player is dead and has not respawned. */
        DEAD,
        /** The player left the server or otherwise went offline. */
        DISCONNECTED
    }
}
