package nl.pim16aap2.lightkeeper.nms.api;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable inputs for a single full-login join driven by {@link IBotLoginDriver#login(BotLoginRequest)}.
 *
 * @param name
 *     Player name to log in as. The server derives the offline UUID from this name.
 * @param port
 *     TCP port of the loopback server to connect to.
 * @param locale
 *     Client locale (e.g. {@code "en_us"}) to send during the configuration phase, or {@code null} for the
 *     server default.
 * @param timeout
 *     Maximum time to wait for the login pipeline to reach the play phase before giving up.
 */
public record BotLoginRequest(
    String name,
    int port,
    @Nullable String locale,
    Duration timeout)
{
    /**
     * Validates the request inputs.
     */
    public BotLoginRequest
    {
        Objects.requireNonNull(name, "name");
        if (name.isBlank())
            throw new IllegalArgumentException("name must not be blank.");
        if (port <= 0 || port > 65_535)
            throw new IllegalArgumentException("port must be in (0, 65535] but was " + port + ".");
        if (locale != null && locale.isBlank())
            throw new IllegalArgumentException("locale must not be blank when present.");
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isNegative() || timeout.isZero())
            throw new IllegalArgumentException("timeout must be positive but was " + timeout + ".");
    }
}
