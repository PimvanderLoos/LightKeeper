package nl.pim16aap2.lightkeeper.provisioning.serverprovider;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.runtime.FileLockLease;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Protects shared JAR/base roots through reuse, rebuild, pruning and copying into a private prepared server. */
final class ServerCacheLease implements AutoCloseable
{
    /** Root leases acquired in canonical path order to prevent overlapping cache configurations from deadlocking. */
    private final List<FileLockLease> leases = new ArrayList<>();

    private ServerCacheLease()
    {
    }

    static ServerCacheLease acquire(Path jarRoot, Path baseRoot, Duration timeout)
    {
        final ServerCacheLease result = new ServerCacheLease();
        try
        {
            final List<Path> roots = List.of(
                Files.createDirectories(jarRoot).toRealPath(),
                Files.createDirectories(baseRoot).toRealPath()
            );
            for (final Path root : roots.stream().distinct().sorted().toList())
            {
                result.leases.add(FileLockLease.acquire(root.resolve(".lightkeeper-locks/cache.lock"), timeout));
            }
            return result;
        }
        catch (IOException | RuntimeException exception)
        {
            try
            {
                result.close();
            }
            catch (RuntimeException cleanupFailure)
            {
                exception.addSuppressed(cleanupFailure);
            }
            throw new ProvisioningException("Cannot acquire shared LightKeeper server caches.", exception);
        }
    }

    /** Releases every root even when one kernel channel reports a cleanup failure. */
    @Override
    public void close()
    {
        RuntimeException failure = null;
        for (final FileLockLease lease : leases.reversed())
        {
            try
            {
                lease.close();
            }
            catch (RuntimeException exception)
            {
                if (failure == null)
                {
                    failure = exception;
                }
                else
                {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null)
        {
            throw failure;
        }
    }
}
