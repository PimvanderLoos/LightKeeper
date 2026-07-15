package nl.pim16aap2.lightkeeper.nms.api;

/**
 * Drives a synthetic player through the full vanilla login pipeline over a real loopback TCP connection.
 *
 * <p>This is the swappable transport seam (LK-18): the reflective in-agent implementation could be replaced
 * by an out-of-process protocol client (e.g. MCProtocolLib) without changing the agent, because the interface
 * is expressed purely in Bukkit/JDK types. Implementations open a TCP connection to the server's own port,
 * traverse the handshake, login, and configuration phases, and block until the play phase is reached or the
 * connection is denied/times out.
 */
public interface IBotLoginDriver
{
    /**
     * Logs a synthetic player in through the full pipeline and blocks until a terminal outcome.
     *
     * <p>Must not be called on the server's main thread: the login runs its own I/O and the server fires the
     * asynchronous pre-login and (main-thread) join events while this method waits.
     *
     * @param request
     *     Login inputs (name, port, locale, timeout).
     * @return
     *     The terminal outcome: joined, denied (with kick reason), or timed out.
     */
    IBotLoginOutcome login(BotLoginRequest request);
}
