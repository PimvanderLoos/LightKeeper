package nl.pim16aap2.lightkeeper.framework;

/**
 * Thrown when a {@code FULL_LOGIN} bot does not reach the play phase (and fire {@code PlayerJoinEvent}) within
 * the agent's bounded timeout.
 */
public class BotJoinTimeoutException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * Creates a timeout exception.
     *
     * @param message
     *     Human-readable timeout detail.
     */
    public BotJoinTimeoutException(String message)
    {
        super(message);
    }
}
