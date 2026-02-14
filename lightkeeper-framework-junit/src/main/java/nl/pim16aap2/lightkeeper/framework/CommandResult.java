package nl.pim16aap2.lightkeeper.framework;

/**
 * Result of executing a command through the framework.
 *
 * @param success
 *     Whether command execution succeeded.
 * @param message
 *     A human-readable result message.
 */
public record CommandResult(
    boolean success,
    String message
)
{
}
