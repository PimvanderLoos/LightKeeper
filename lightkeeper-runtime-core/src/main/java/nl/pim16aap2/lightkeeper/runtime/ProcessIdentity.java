package nl.pim16aap2.lightkeeper.runtime;

import java.time.Instant;

/**
 * Process identity that remains unambiguous when the operating system reuses a PID.
 *
 * @param processId
 *     Operating-system process identifier.
 * @param processStart
 *     Exact process start instant reported by the operating system.
 */
public record ProcessIdentity(long processId, String processStart)
{
    /** Validates the PID and start instant. */
    public ProcessIdentity
    {
        if (processId <= 0)
        {
            throw new IllegalArgumentException("Process identity requires a positive PID.");
        }
        Instant.parse(processStart);
    }

    /**
     * Captures this JVM's operating-system identity.
     *
     * @return Current process identity.
     */
    public static ProcessIdentity current()
    {
        final ProcessHandle process = ProcessHandle.current();
        return new ProcessIdentity(process.pid(), process.info().startInstant().orElseThrow().toString());
    }

    /**
     * Checks the actual process ancestry rather than trusting a persistent Maven flag or a PID alone.
     *
     * @return Whether this identity matches the current process or one of its live ancestors.
     */
    public boolean matchesCurrentAncestry()
    {
        ProcessHandle candidate = ProcessHandle.current();
        while (true)
        {
            if (candidate.pid() == processId)
            {
                return candidate.isAlive() && candidate.info().startInstant()
                    .map(start -> start.toString().equals(processStart))
                    .orElse(false);
            }
            final var parent = candidate.parent();
            if (parent.isEmpty())
            {
                return false;
            }
            candidate = parent.orElseThrow();
        }
    }
}
