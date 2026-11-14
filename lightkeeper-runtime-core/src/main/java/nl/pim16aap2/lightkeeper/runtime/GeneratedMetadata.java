package nl.pim16aap2.lightkeeper.runtime;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Bounded JSON reads and atomic publication of LK-owned generated build metadata. */
public final class GeneratedMetadata
{
    /** Upper bound shared by generated plans and process lease records. */
    private static final int MAX_BYTES = 1_048_576;
    /** Thread-safe mapper configured before any concurrent reads or writes. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private GeneratedMetadata()
    {
    }

    /**
     * Publishes a complete JSON document inside a user-only directory.
     *
     * @param value
     *     Metadata to serialize; callers must exclude credentials.
     * @param path
     *     Destination document.
     * @throws IOException
     *     If publication fails.
     */
    public static void write(Object value, Path path)
        throws IOException
    {
        final Path destination = path.toAbsolutePath().normalize();
        final Path parent = Objects.requireNonNull(destination.getParent(), "Metadata path must have a parent.");
        UserOnlyDirectory.prepare(parent);
        final Path temporary = Files.createTempFile(parent, ".lk-metadata-", ".tmp");
        try
        {
            MAPPER.writeValue(temporary.toFile(), value);
            try
            {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException exception)
            {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (JacksonException exception)
        {
            throw new IOException("Failed to serialize generated metadata '" + destination + "'.", exception);
        }
        finally
        {
            Files.deleteIfExists(temporary);
        }
    }

    /**
     * Reads one bounded regular JSON file without following a symbolic link.
     *
     * @param path
     *     Published metadata document.
     * @param type
     *     Expected metadata type, whose constructor validates its own schema and invariants.
     * @param <T>
     *     Metadata type.
     * @return Parsed non-null document.
     * @throws IOException
     *     If metadata is missing, oversized, malformed, or not a regular file.
     */
    public static <T> T read(Path path, Class<T> type)
        throws IOException
    {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        {
            throw new IOException("Generated metadata is not a regular file: " + path);
        }
        try (InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS))
        {
            final byte[] bytes = stream.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES)
            {
                throw new IOException("Generated metadata exceeds " + MAX_BYTES + " bytes: " + path);
            }
            final T value = MAPPER.readValue(bytes, type);
            if (value == null)
            {
                throw new IOException("Generated metadata may not be null: " + path);
            }
            return value;
        }
        catch (JacksonException exception)
        {
            throw new IOException("Failed to parse generated metadata '" + path + "'.", exception);
        }
    }
}
