package nl.pim16aap2.lightkeeper.maven;

import java.net.URI;

/**
 * Metadata for a single downloadable Paper build.
 *
 * @param minecraftVersion
 *     The Minecraft version for this Paper build.
 * @param buildId
 *     The Paper build identifier.
 * @param downloadUri
 *     The URI for the server JAR download.
 * @param sha256
 *     The expected SHA-256 checksum for the server JAR.
 */
public record PaperBuildMetadata(
    String minecraftVersion,
    long buildId,
    URI downloadUri,
    String sha256
)
{
}
