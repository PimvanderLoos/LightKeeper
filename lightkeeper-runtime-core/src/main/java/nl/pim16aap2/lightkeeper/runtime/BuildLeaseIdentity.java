package nl.pim16aap2.lightkeeper.runtime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Identity of the live process holding a cooperative producer-build lease.
 *
 * @param reactorDirectory
 *     Canonical checkout/reactor root whose build outputs are protected.
 * @param process
 *     Lease-owning process PID and start instant, preventing PID-reuse confusion.
 * @param nonce
 *     Random invocation identity; pass to a bootstrap child privately, never in progress logs.
 */
public record BuildLeaseIdentity(String reactorDirectory, ProcessIdentity process, String nonce)
{
    /** Validates the process identity before it can be used for borrowing. */
    public BuildLeaseIdentity
    {
        if (!Path.of(reactorDirectory).isAbsolute())
        {
            throw new IllegalArgumentException("A build lease requires an absolute reactor path and positive PID.");
        }
        Objects.requireNonNull(process, "process may not be null.");
        if (!Objects.requireNonNull(nonce, "nonce may not be null.").matches("[a-f0-9]{32}"))
        {
            throw new IllegalArgumentException("A build lease requires a 128-bit invocation nonce.");
        }
    }

}
