package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative checkout-wide ownership covering producer packaging through immutable snapshot capture.
 * Release before starting Minecraft. The underlying file lease keeps a stable inode for waiting processes.
 */
public final class BuildOutputLease implements AutoCloseable
{
    /** Kernel ownership shared with cache coordination through the canonical file-lease implementation. */
    private final FileLockLease lease;
    /** Private identity removed before kernel ownership is released. */
    private final Path ownerFile;
    /** Actual producer JVM and unique invocation identity. */
    private final BuildLeaseIdentity identity;
    /** Prevents repeated cleanup by hooks and normal lifecycle callbacks. */
    private final AtomicBoolean closed = new AtomicBoolean();

    private BuildOutputLease(FileLockLease lease, Path ownerFile, BuildLeaseIdentity identity)
    {
        this.lease = lease;
        this.ownerFile = ownerFile;
        this.identity = identity;
    }

    /**
     * Waits for exclusive access to this reactor's producer outputs.
     *
     * @param reactorDirectory
     *     Existing checkout/reactor root.
     * @param timeout
     *     Maximum positive wait duration.
     * @return Acquired ownership; close after immutable capture.
     */
    public static BuildOutputLease acquire(Path reactorDirectory, Duration timeout)
    {
        if (timeout.isZero() || timeout.isNegative())
        {
            throw new IllegalArgumentException("Build lease timeout must be positive.");
        }
        try
        {
            final Path reactor = reactorDirectory.toRealPath();
            final Path lockPath = lockPath(reactor);
            final FileLockLease lease = FileLockLease.acquire(lockPath, timeout);
            try
            {
                final BuildLeaseIdentity identity = new BuildLeaseIdentity(
                    reactor.toString(), ProcessIdentity.current(), UUID.randomUUID().toString().replace("-", "")
                );
                final Path ownerFile = lockPath.resolveSibling("owner.json");
                GeneratedMetadata.write(identity, ownerFile);
                return new BuildOutputLease(lease, ownerFile, identity);
            }
            catch (IOException | RuntimeException exception)
            {
                try
                {
                    lease.close();
                }
                catch (RuntimeException cleanupFailure)
                {
                    exception.addSuppressed(cleanupFailure);
                }
                throw exception;
            }
        }
        catch (IOException | RuntimeException exception)
        {
            throw new IllegalStateException("Cannot acquire producer build lease for '" + reactorDirectory + "'.",
                exception);
        }
    }

    /**
     * Returns the held invocation identity without transferring ownership.
     *
     * @return Current lease identity.
     */
    public BuildLeaseIdentity identity()
    {
        return identity;
    }

    /**
     * Returns the stable kernel lock location for a reactor root.
     *
     * @param reactorDirectory
     *     Checkout/reactor root.
     * @return Lock file retained across invocations.
     */
    public static Path lockPath(Path reactorDirectory)
    {
        return reactorDirectory.toAbsolutePath().normalize().resolve(".lightkeeper/build/outputs.lock");
    }

    /** Removes the process identity while ownership is held, then releases the kernel lease. */
    @Override
    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            try (lease)
            {
                Files.deleteIfExists(ownerFile);
            }
            catch (IOException exception)
            {
                throw new IllegalStateException("Failed to release LightKeeper producer build lease.", exception);
            }
        }
    }
}
