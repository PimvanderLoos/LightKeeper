package nl.pim16aap2.lightkeeper.maven;

import java.net.URI;

/**
 * Resolved Modrinth plugin file metadata.
 *
 * @param versionId
 *     Modrinth version ID.
 * @param versionNumber
 *     Modrinth version number.
 * @param fileName
 *     Downloaded plugin file name.
 * @param downloadUri
 *     Direct file download URI.
 * @param sha512
 *     Modrinth-provided SHA-512 file checksum.
 */
public record ModrinthPluginMetadata(
    String versionId,
    String versionNumber,
    String fileName,
    URI downloadUri,
    String sha512
)
{
}
