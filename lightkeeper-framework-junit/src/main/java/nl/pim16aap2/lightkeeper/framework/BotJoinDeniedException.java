package nl.pim16aap2.lightkeeper.framework;

/**
 * Thrown when a {@code FULL_LOGIN} bot is refused by the server (whitelist, ban, full server, or a plugin
 * denial at pre-login/login).
 *
 * <p>The message carries the protocol phase the connection was denied in and the server's kick reason text,
 * so consumers can assert on denials as typed errors rather than parsing generic failures.
 */
public class BotJoinDeniedException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * Creates a denial exception.
     *
     * @param message
     *     Human-readable denial detail, including the phase and the server's kick reason.
     */
    public BotJoinDeniedException(String message)
    {
        super(message);
    }
}
