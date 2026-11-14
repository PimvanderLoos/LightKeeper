package nl.pim16aap2.lightkeeper.runtime;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Exclusive process/thread coordination without replacing or deleting the shared kernel-lock inode. */
public final class FileLockLease implements AutoCloseable
{
    /** Prevents another descriptor's close from releasing this JVM's POSIX lock on the same file. */
    private static final ConcurrentHashMap<Path, Semaphore> JVM_LEASES = new ConcurrentHashMap<>();
    /** Maximum delay before observing cancellation or retrying a competing process's lock. */
    private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);
    /** Channel retained for the entire kernel-lock lifetime. */
    private final FileChannel channel;
    /** Exclusive kernel lock. */
    private final FileLock lock;
    /** JVM permit acquired before opening any descriptor for this lock file. */
    private final Semaphore permit;
    /** Ensures hooks and normal cleanup cannot close a lease twice. */
    private final AtomicBoolean closed = new AtomicBoolean();

    private FileLockLease(FileChannel channel, FileLock lock, Semaphore permit)
    {
        this.channel = channel;
        this.lock = lock;
        this.permit = permit;
    }

    /**
     * Acquires one stable lock file, respecting timeout and interruption.
     *
     * @param lockPath
     *     Lock in an LK-owned directory, outside any generated tree that callers may replace or prune.
     * @param timeout
     *     Maximum positive acquisition duration.
     * @return Owned kernel lease; close after every protected read/copy/write has completed.
     */
    public static FileLockLease acquire(Path lockPath, Duration timeout)
    {
        if (timeout.isZero() || timeout.isNegative())
        {
            throw new IllegalArgumentException("File lease timeout must be positive.");
        }
        FileChannel channel = null;
        Semaphore permit = null;
        boolean acquiredPermit = false;
        try
        {
            final long started = System.nanoTime();
            final Path absolute = lockPath.toAbsolutePath().normalize();
            final Path parent = Objects.requireNonNull(absolute.getParent(), "Lock file must have a parent.");
            UserOnlyDirectory.prepare(parent);
            final Path canonical = parent.toRealPath().resolve(absolute.getFileName());
            permit = JVM_LEASES.computeIfAbsent(canonical, ignored -> new Semaphore(1, true));
            acquiredPermit = permit.tryAcquire(timeout.toNanos(), TimeUnit.NANOSECONDS);
            if (!acquiredPermit)
            {
                throw new IllegalStateException("Timed out waiting for the JVM's file lease: " + canonical);
            }
            channel = FileChannel.open(canonical, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
            while (true)
            {
                final FileLock lock = tryLock(channel);
                if (lock != null)
                {
                    return new FileLockLease(channel, lock, permit);
                }
                final long remaining = timeout.toNanos() - (System.nanoTime() - started);
                if (remaining <= 0)
                {
                    throw new IllegalStateException("Timed out waiting for file lease '" + canonical + "'.");
                }
                TimeUnit.NANOSECONDS.sleep(Math.min(POLL_NANOS, remaining));
            }
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            cleanupFailedAcquisition(channel, permit, acquiredPermit, exception);
            throw new IllegalStateException("Interrupted while waiting for file lease '" + lockPath + "'.", exception);
        }
        catch (IOException | RuntimeException exception)
        {
            cleanupFailedAcquisition(channel, permit, acquiredPermit, exception);
            throw new IllegalStateException("Cannot acquire file lease '" + lockPath + "'.", exception);
        }
    }

    /** Releases the kernel channel before allowing another thread to open its descriptor. */
    @Override
    public void close()
    {
        if (closed.compareAndSet(false, true))
        {
            try (channel; lock)
            {
                // Closing resources releases the kernel lease while retaining the inode for waiting processes.
            }
            catch (IOException exception)
            {
                throw new IllegalStateException("Failed to release file lease.", exception);
            }
            finally
            {
                permit.release();
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

    private static void cleanupFailedAcquisition(
        @Nullable FileChannel channel,
        @Nullable Semaphore permit,
        boolean acquiredPermit,
        Exception failure
    )
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
        if (acquiredPermit && permit != null)
        {
            permit.release();
        }
    }
}
