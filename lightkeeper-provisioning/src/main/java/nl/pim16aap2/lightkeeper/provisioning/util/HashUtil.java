package nl.pim16aap2.lightkeeper.provisioning.util;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;

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
    /** Bytes read per hashing iteration. */
    private static final int BUFFER_SIZE = 8_192;

    private HashUtil()
    {
    }

    /**
     * Computes the SHA-256 of a file without modifying it.
     *
     * @param path
     *     Input file.
     * @return Lowercase hexadecimal digest.
     * @throws ProvisioningException
     *     If the file cannot be read.
     */
    public static String sha256(Path path)
        throws ProvisioningException
    {
        return hash(path, "SHA-256");
    }

    /**
     * Computes SHA-256 while leaving stream ownership with the caller.
     *
     * @param inputStream
     *     Input consumed until EOF.
     * @return Lowercase hexadecimal digest.
     * @throws ProvisioningException
     *     If reading fails.
     */
    public static String sha256(InputStream inputStream)
        throws ProvisioningException
    {
        return hash(inputStream, "SHA-256");
    }

    /**
     * Computes the SHA-512 of a file without modifying it.
     *
     * @param path
     *     Input file.
     * @return Lowercase hexadecimal digest.
     * @throws ProvisioningException
     *     If the file cannot be read.
     */
    public static String sha512(Path path)
        throws ProvisioningException
    {
        return hash(path, "SHA-512");
    }

    /**
     * Computes SHA-512 while leaving stream ownership with the caller.
     *
     * @param inputStream
     *     Input consumed until EOF.
     * @return Lowercase hexadecimal digest.
     * @throws ProvisioningException
     *     If reading fails.
     */
    public static String sha512(InputStream inputStream)
        throws ProvisioningException
    {
        return hash(inputStream, "SHA-512");
    }

    private static String hash(Path path, String algorithm)
        throws ProvisioningException
    {
        try (InputStream inputStream = Files.newInputStream(path))
        {
            return hash(inputStream, algorithm);
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to compute %s for file '%s'.".formatted(algorithm, path),
                exception
            );
        }
    }

    private static String hash(InputStream inputStream, String algorithm)
        throws ProvisioningException
    {
        try
        {
            final MessageDigest digest = getDigest(algorithm);
            final byte[] buffer = new byte[BUFFER_SIZE];

            int readBytes;
            while ((readBytes = inputStream.read(buffer)) != -1)
            {
                digest.update(buffer, 0, readBytes);
            }

            return toHex(digest.digest());
        }
        catch (IOException exception)
        {
            throw new ProvisioningException("Failed to compute %s from input stream.".formatted(algorithm), exception);
        }
    }

    /**
     * Computes SHA-256 for an in-memory input.
     *
     * @param input
     *     Bytes to hash.
     * @return Lowercase hexadecimal digest.
     */
    public static String sha256(byte[] input)
    {
        final MessageDigest digest = getSha256Digest();
        digest.update(input);
        return toHex(digest.digest());
    }

    /**
     * Computes SHA-256 using the string's UTF-8 encoding.
     *
     * @param input
     *     Text to hash.
     * @return Lowercase hexadecimal digest.
     */
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
