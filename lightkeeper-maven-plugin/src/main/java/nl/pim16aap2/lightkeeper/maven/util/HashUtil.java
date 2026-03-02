package nl.pim16aap2.lightkeeper.maven.util;

import org.apache.maven.plugin.MojoExecutionException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Utility methods for SHA-256 hashing.
 */
public final class HashUtil
{
    private static final int BUFFER_SIZE = 8_192;

    private HashUtil()
    {
    }

    public static String sha256(Path path)
        throws MojoExecutionException
    {
        try (InputStream inputStream = Files.newInputStream(path))
        {
            final MessageDigest digest = getSha256Digest();
            final byte[] buffer = new byte[BUFFER_SIZE];

            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1)
                digest.update(buffer, 0, readBytes);

            return toHex(digest.digest());
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to compute SHA-256 for file '%s'.".formatted(path), exception);
        }
    }

    public static String sha256(String input)
    {
        final MessageDigest digest = getSha256Digest();
        digest.update(input.getBytes(StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }

    private static MessageDigest getSha256Digest()
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

    private static String toHex(byte[] bytes)
    {
        final StringBuilder ret = new StringBuilder(bytes.length * 2);
        for (final byte value : bytes)
            ret.append(Character.forDigit((value >>> 4) & 0xF, 16)).append(Character.forDigit(value & 0xF, 16));
        return ret.toString();
    }
}
