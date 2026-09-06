package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.jspecify.annotations.Nullable;

/**
 * Cross-process lease preventing concurrent use or replacement of a module's IDE runtime.
 */
public final class IdeRuntimeLock implements AutoCloseable
{
    private final Path path;
    private final FileChannel channel;
    private final FileLock lock;

    private IdeRuntimeLock(Path path, FileChannel channel, FileLock lock)
    {
        this.path = path;
        this.channel = channel;
        this.lock = lock;
    }

    /**
     * Attempts to acquire the module-wide IDE runtime lock without waiting.
     *
     * @param moduleDirectory Owning Maven module directory.
     * @return The acquired lease.
     */
    public static IdeRuntimeLock acquire(Path moduleDirectory)
    {
        final Path lockPath = IdeRuntimePaths.lockFile(moduleDirectory);
        FileChannel channel = null;
        try
        {
            Files.createDirectories(lockPath.getParent());
            channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            final FileLock lock = channel.tryLock();
            if (lock == null)
            {
                channel.close();
                throw busy(lockPath);
            }
            return new IdeRuntimeLock(lockPath, channel, lock);
        }
        catch (OverlappingFileLockException exception)
        {
            closeQuietly(channel);
            throw busy(lockPath, exception);
        }
        catch (IOException exception)
        {
            closeQuietly(channel);
            throw new IllegalStateException("Failed to acquire IDE runtime lock '%s'.".formatted(lockPath), exception);
        }
    }

    @Override
    public void close()
    {
        try
        {
            lock.release();
            channel.close();
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to release IDE runtime lock '%s'.".formatted(path), exception);
        }
    }

    private static IllegalStateException busy(Path lockPath)
    {
        return new IllegalStateException(busyMessage(lockPath));
    }

    private static IllegalStateException busy(Path lockPath, Exception exception)
    {
        return new IllegalStateException(busyMessage(lockPath), exception);
    }

    private static String busyMessage(Path lockPath)
    {
        return "IDE runtime for module is busy (lock '%s'). Close the running test/server and retry."
            .formatted(lockPath);
    }

    private static void closeQuietly(@Nullable FileChannel channel)
    {
        if (channel == null)
            return;
        try
        {
            channel.close();
        }
        catch (IOException ignored)
        {
            // Preserve the acquisition failure.
        }
    }
}
