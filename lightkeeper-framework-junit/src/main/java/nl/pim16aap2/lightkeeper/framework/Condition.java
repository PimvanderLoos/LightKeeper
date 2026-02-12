package nl.pim16aap2.lightkeeper.framework;

/**
 * Condition callback used by {@link LightkeeperFramework#waitUntil(Condition, java.time.Duration)}.
 */
@FunctionalInterface
public interface Condition
{
    /**
     * Evaluates whether the condition has been met.
     *
     * @return {@code true} when the condition is satisfied.
     * @throws Exception
     *     When evaluation fails.
     */
    boolean evaluate()
        throws Exception;
}
