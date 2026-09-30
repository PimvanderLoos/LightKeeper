package nl.pim16aap2.lightkeeper.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

/**
 * Canonically hashes files and directory trees used by IDE runtime preparation and validation.
 */
public final class IdeRuntimeArtifactHasher
{
    private static final int BUFFER_SIZE = 8_192;

    private IdeRuntimeArtifactHasher()
    {
    }

    /**
     * Computes a SHA-256 identity for a regular file or directory tree.
     *
     * @param input Input path.
     * @return Lowercase hexadecimal SHA-256.
     * @throws IOException When the input is missing, contains a symbolic link, or cannot be read.
     */
    public static String sha256(Path input)
        throws IOException
    {
        if (Files.isRegularFile(input))
            return hashFile(input);
        if (!Files.isDirectory(input))
            throw new IOException("IDE preparation input '%s' does not exist.".formatted(input));

        final List<Path> entries;
        try (Stream<Path> stream = Files.walk(input))
        {
            entries = stream
                .filter(path -> !path.equals(input))
                .sorted(Comparator.comparing(path -> normalizedRelativePath(input, path)))
                .toList();
        }
        final MessageDigest digest = digest();
        for (final Path entry : entries)
        {
            if (Files.isSymbolicLink(entry))
                throw new IOException("Symbolic links are not allowed in IDE preparation inputs: " + entry);
            update(digest, Files.isDirectory(entry) ? "directory:" : "file:");
            update(digest, normalizedRelativePath(input, entry));
            if (Files.isRegularFile(entry))
                update(digest, hashFile(entry));
            update(digest, "\n");
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String hashFile(Path file)
        throws IOException
    {
        final MessageDigest digest = digest();
        try (InputStream inputStream = Files.newInputStream(file))
        {
            final byte[] buffer = new byte[BUFFER_SIZE];
            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1)
                digest.update(buffer, 0, readBytes);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest digest()
    {
        try
        {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("SHA-256 is unavailable in the current JVM.", exception);
        }
    }

    private static void update(MessageDigest digest, String value)
    {
        digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String normalizedRelativePath(Path root, Path path)
    {
        return root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
    }
}
