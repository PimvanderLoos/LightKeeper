package nl.pim16aap2.lightkeeper.nms.api;

import java.util.Objects;

/**
 * Terminal result of a full-login join attempt.
 *
 * <p>Exactly one of the permitted subtypes is produced: {@link Joined} when the login pipeline reached the
 * play phase, {@link Denied} when the server kicked the connection (whitelist/ban/full/plugin denial), or
 * {@link TimedOut} when the pipeline did not complete in time.
 */
public sealed interface IBotLoginOutcome
    permits IBotLoginOutcome.Joined, IBotLoginOutcome.Denied, IBotLoginOutcome.TimedOut
{
    /**
     * The login pipeline reached the play phase; the server has accepted the player.
     *
     * @param name
     *     Name the player logged in with.
     */
    record Joined(String name) implements IBotLoginOutcome
    {
        /**
         * Validates the outcome inputs.
         */
        public Joined
        {
            Objects.requireNonNull(name, "name");
        }
    }

    /**
     * The server refused the connection with a kick reason.
     *
     * @param phase
     *     Protocol phase the connection was in when it was denied.
     * @param reason
     *     Human-readable kick reason text extracted from the server's disconnect packet.
     */
    record Denied(BotJoinPhase phase, String reason) implements IBotLoginOutcome
    {
        /**
         * Validates the outcome inputs.
         */
        public Denied
        {
            Objects.requireNonNull(phase, "phase");
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The login pipeline did not reach the play phase within the requested timeout.
     */
    record TimedOut() implements IBotLoginOutcome
    {
    }
}
