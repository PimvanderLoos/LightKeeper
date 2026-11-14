package nl.pim16aap2.lightkeeper.runtime;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cooperative checkout-wide lease covering producer packaging through immutable snapshot capture.
 * Release before starting Minecraft. Lock files remain in place so waiting processes always lock the same inode.
 */
public final class BuildOutputLease implements AutoCloseable
{
    /** Private child environment entry authorizing a descendant Maven build to borrow the parent's lease. */
    public static final String BORROW_NONCE_ENV = "LIGHTKEEPER_BUILD_LEASE_NONCE";
    /** Name of the current holder's private process identity document. */
    private static final String OWNER_FILE = "owner.json";
    /** Maximum interval before observing cancellation or an available lease again. */
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    /** Avoids opening another descriptor for a file already locked by this JVM, including failed acquisitions. */
    private static final ConcurrentHashMap<Path, Semaphore> JVM_LEASES = new ConcurrentHashMap<>();
    /** Channel whose lifetime owns the kernel lease. */
    private final FileChannel channel;
    /** Exclusive kernel lock acquired before any protected build output is changed. */
    private final FileLock lock;
    /** Metadata removed while the kernel lease is still held. */
    private final Path ownerFile;
    /** Identity offered only to a verified descendant bootstrap build. */
    private final BuildLeaseIdentity identity;
    /** Prevents repeated cleanup by shutdown hooks and ordinary lifecycle callbacks. */
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Process-local permit retained until the kernel channel is closed. */
    private final Semaphore jvmLease;

    private BuildOutputLease(
        FileChannel channel,
        FileLock lock,
        Path ownerFile,
        BuildLeaseIdentity identity,
        Semaphore jvmLease
    )
    {
        this.channel = channel;
        this.lock = lock;
        this.ownerFile = ownerFile;
        this.identity = identity;
        this.jvmLease = jvmLease;
    }

    /**
     * Waits for exclusive access to this reactor's producer outputs, respecting timeout and interruption.
     *
     * @param reactorDirectory
     *     Existing checkout/reactor root.
     * @param timeout
     *     Maximum positive wait duration.
     * @return Acquired lease; callers transfer ownership explicitly and close it after capture.
     */
    public static BuildOutputLease acquire(Path reactorDirectory, Duration timeout)
    {
        if (timeout.isZero() || timeout.isNegative())
        {
            throw new IllegalArgumentException("Build lease timeout must be positive.");
        }
        FileChannel channel = null;
        Semaphore jvmLease = null;
        boolean hasPermit = false;
        try
        {
            final long started = System.nanoTime();
            final Path reactor = reactorDirectory.toRealPath();
            final Path lockPath = lockPath(reactor);
            jvmLease = JVM_LEASES.computeIfAbsent(reactor, ignored -> new Semaphore(1, true));
            hasPermit = jvmLease.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (!hasPermit)
            {
                throw new IllegalStateException("Timed out waiting for the JVM's producer build lease: " + lockPath);
            }
            UserOnlyDirectory.prepare(Objects.requireNonNull(lockPath.getParent()));
            channel = FileChannel.open(
                lockPath,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS
            );
            while (true)
            {
                final FileLock lock = tryLock(channel);
                if (lock != null)
                {
                    final ProcessHandle owner = ProcessHandle.current();
                    final BuildLeaseIdentity identity = new BuildLeaseIdentity(
                        reactor.toString(),
                        owner.pid(),
                        owner.info().startInstant().orElseThrow().toString(),
                        UUID.randomUUID().toString().replace("-", "")
                    );
                    final Path ownerFile = lockPath.resolveSibling(OWNER_FILE);
                    GeneratedMetadata.write(identity, ownerFile);
                    return new BuildOutputLease(channel, lock, ownerFile, identity, jvmLease);
                }
                final long remaining = timeout.toNanos() - (System.nanoTime() - started);
                if (remaining <= 0)
                {
                    throw new IllegalStateException(
                        "Timed out waiting for LightKeeper producer build lease '" + lockPath + "'."
                    );
                }
                TimeUnit.NANOSECONDS.sleep(Math.min(POLL_NANOS, remaining));
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            closeFailedAcquisition(channel, exception);
            if (hasPermit && jvmLease != null)
            {
                jvmLease.release();
            }
            throw new IllegalStateException(
                "Interrupted while waiting for the LightKeeper producer build lease.",
                exception
            );
        }
        catch (IOException | RuntimeException exception)
        {
            closeFailedAcquisition(channel, exception);
            if (hasPermit && jvmLease != null)
            {
                jvmLease.release();
            }
            throw new IllegalStateException(
                "Cannot acquire producer build lease for '" + reactorDirectory + "'.",
                exception
            );
        }
    }

    /**
     * Validates a child build's borrowed lease without reacquiring or releasing its parent's kernel lock.
     *
     * @param reactorDirectory
     *     Reactor selected by the child build.
     * @param nonce
     *     Parent invocation identity received through the child environment.
     * @return Verified live ancestor identity.
     */
    public static BuildLeaseIdentity verifyBorrowed(Path reactorDirectory, String nonce)
    {
        try
        {
            final Path reactor = reactorDirectory.toRealPath();
            final Path lockPath = lockPath(reactor);
            final Path ownerFile = lockPath.resolveSibling(OWNER_FILE);
            final BuildLeaseIdentity owner = GeneratedMetadata.read(ownerFile, BuildLeaseIdentity.class);
            if (owner.processId() == ProcessHandle.current().pid() ||
                !owner.reactorDirectory().equals(reactor.toString()) || !owner.nonce().equals(nonce) ||
                !owner.isCurrentProcessAncestor())
            {
                throw new IllegalStateException("Borrowed build lease does not belong to a live ancestor invocation.");
            }
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS))
            {
                try (FileLock probe = tryLock(channel))
                {
                    if (probe != null)
                    {
                        throw new IllegalStateException("Borrowed producer build lease is no longer held.");
                    }
                }
            }
            if (!owner.equals(GeneratedMetadata.read(ownerFile, BuildLeaseIdentity.class)))
            {
                throw new IllegalStateException("Borrowed producer build lease changed while checking ownership.");
            }
            return owner;
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Cannot verify borrowed producer build lease for '" + reactorDirectory + "'.",
                exception
            );
        }
    }

    /**
     * Returns the held invocation identity for a child environment, without logging its nonce.
     *
     * @return Current lease identity.
     */
    public BuildLeaseIdentity identity()
    {
        return identity;
    }

    /**
     * Returns the canonical generated lock location for a reactor root.
     *
     * @param reactorDirectory
     *     Checkout/reactor root.
     * @return Stable lock file path.
     */
    public static Path lockPath(Path reactorDirectory)
    {
        return reactorDirectory.toAbsolutePath().normalize().resolve(".lightkeeper/build/outputs.lock");
    }

    /** Releases the process identity and kernel lease, retaining the lock file for waiting processes. */
    @Override
    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            try (channel; lock)
            {
                Files.deleteIfExists(ownerFile);
            }
            catch (IOException exception)
            {
                throw new IllegalStateException("Failed to release LightKeeper producer build lease.", exception);
            }
            finally
            {
                jvmLease.release();
            }
        }
    }

    private static @Nullable FileLock tryLock(FileChannel channel)
        throws IOException
    {
        try
        {
            return channel.tryLock();
        }
        catch (OverlappingFileLockException exception)
        {
            return null;
        }
    }

    private static void closeFailedAcquisition(@Nullable FileChannel channel, Exception failure)
    {
        if (channel != null)
        {
            try
            {
                channel.close();
            }
            catch (IOException cleanupFailure)
            {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }
}
