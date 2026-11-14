package nl.pim16aap2.lightkeeper.provisioning.serverprovider;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class ServerCacheLeaseTest
{
    @TempDir
    private Path root;

    @Test
    void acquire_shouldCoordinateOppositeRootOrdersAndReleaseAfterClose()
    {
        // setup
        final Path jars = root.resolve("jars");
        final Path bases = root.resolve("bases");
        try (var held = ServerCacheLease.acquire(jars, bases, Duration.ofSeconds(1)))
        {
            // execute
            final Throwable failure = catchThrowable(() ->
                ServerCacheLease.acquire(bases, jars, Duration.ofMillis(30)));

            // verify
            assertThat(failure).isInstanceOf(ProvisioningException.class).hasRootCauseMessage(
                "Timed out waiting for the JVM's file lease: " + bases.resolve(".lightkeeper-locks/cache.lock")
            );
        }
        try (var next = ServerCacheLease.acquire(bases, jars, Duration.ofMillis(100)))
        {
            assertThat(jars.resolve(".lightkeeper-locks/cache.lock")).isRegularFile();
            assertThat(bases.resolve(".lightkeeper-locks/cache.lock")).isRegularFile();
        }
    }

    @Test
    void acquire_shouldDeduplicateIdenticalRootsAndAliases()
        throws Exception
    {
        // setup
        final Path cache = Files.createDirectories(root.resolve("cache"));
        final Path alias = Files.createSymbolicLink(root.resolve("alias"), cache);

        // execute
        try (var held = ServerCacheLease.acquire(cache, alias, Duration.ofMillis(100)))
        {
            // verify
            assertThat(cache.resolve(".lightkeeper-locks/cache.lock")).isRegularFile();
        }
    }

    @Test
    void acquire_shouldReleaseEarlierRootsWhenALaterRootCannotBeAcquired()
    {
        // setup
        final Path first = root.resolve("a");
        final Path last = root.resolve("z");
        try (var held = ServerCacheLease.acquire(last, last, Duration.ofSeconds(1)))
        {
            // execute
            final Throwable failure = catchThrowable(() ->
                ServerCacheLease.acquire(first, last, Duration.ofMillis(30)));

            // verify
            assertThat(failure).isInstanceOf(ProvisioningException.class);
            try (var recovered = ServerCacheLease.acquire(first, first, Duration.ofMillis(100)))
            {
                assertThat(first.resolve(".lightkeeper-locks/cache.lock")).isRegularFile();
            }
        }
    }
}
