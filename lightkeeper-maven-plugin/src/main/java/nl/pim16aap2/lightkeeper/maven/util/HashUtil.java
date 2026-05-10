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
 * Utility methods for SHA-256 and SHA-512 hashing.
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
        return hash(path, "SHA-256");
    }

    public static String sha256(InputStream inputStream)
        throws MojoExecutionException
    {
        return hash(inputStream, "SHA-256");
    }

    public static String sha512(Path path)
        throws MojoExecutionException
    {
        return hash(path, "SHA-512");
    }

    public static String sha512(InputStream inputStream)
        throws MojoExecutionException
    {
        return hash(inputStream, "SHA-512");
    }

    private static String hash(Path path, String algorithm)
        throws MojoExecutionException
    {
        try (InputStream inputStream = Files.newInputStream(path))
        {
            return hash(inputStream, algorithm);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to compute %s for file '%s'.".formatted(algorithm, path),
                exception
            );
        }
    }

    private static String hash(InputStream inputStream, String algorithm)
        throws MojoExecutionException
    {
        try
        {
            final MessageDigest digest = getDigest(algorithm);
            final byte[] buffer = new byte[BUFFER_SIZE];

            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1)
                digest.update(buffer, 0, readBytes);

            return toHex(digest.digest());
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to compute %s from input stream.".formatted(algorithm), exception);
        }
    }

    public static String sha256(byte[] input)
    {
        final MessageDigest digest = getSha256Digest();
        digest.update(input);
        return toHex(digest.digest());
    }

    public static String sha256(String input)
    {
        final MessageDigest digest = getSha256Digest();
        digest.update(input.getBytes(StandardCharsets.UTF_8));
        return toHex(digest.digest());
    }

    private static MessageDigest getSha256Digest()
    {
        return getDigest("SHA-256");
    }

    private static MessageDigest getDigest(String algorithm)
    {
        try
        {
            return MessageDigest.getInstance(algorithm);
        }
        catch (NoSuchAlgorithmException exception)
        {
            throw new IllegalStateException("%s is unavailable in the current JVM.".formatted(algorithm), exception);
        }
    }

    private static String toHex(byte[] bytes)
    {
        return java.util.HexFormat.of().formatHex(bytes);
    }
}
