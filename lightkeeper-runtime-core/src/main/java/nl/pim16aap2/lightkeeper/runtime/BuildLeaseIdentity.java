package nl.pim16aap2.lightkeeper.runtime;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * Identity of the live process holding a cooperative producer-build lease.
 *
 * @param reactorDirectory
 *     Canonical checkout/reactor root whose build outputs are protected.
 * @param processId
 *     Lease-owning process identifier.
 * @param processStart
 *     Process start instant, preventing PID-reuse confusion.
 * @param nonce
 *     Random invocation identity; pass to a bootstrap child privately, never in progress logs.
 */
public record BuildLeaseIdentity(String reactorDirectory, long processId, String processStart, String nonce)
{
    /** Validates the process identity before it can be used for borrowing. */
    public BuildLeaseIdentity
    {
        if (!Path.of(reactorDirectory).isAbsolute() || processId <= 0)
        {
            throw new IllegalArgumentException("A build lease requires an absolute reactor path and positive PID.");
        }
        Instant.parse(processStart);
        if (!Objects.requireNonNull(nonce, "nonce may not be null.").matches("[a-f0-9]{32}"))
        {
            throw new IllegalArgumentException("A build lease requires a 128-bit invocation nonce.");
        }
    }

    /**
     * Verifies ownership against the actual current process or one of its live ancestors.
     *
     * @return Whether PID and start instant both match the current process ancestry.
     */
    public boolean isCurrentProcessAncestor()
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
