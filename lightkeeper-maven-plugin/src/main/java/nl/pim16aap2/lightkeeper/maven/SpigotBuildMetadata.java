package nl.pim16aap2.lightkeeper.maven;

import java.net.URI;

/**
 * Metadata used to build a Spigot server jar with BuildTools.
 *
 * @param minecraftVersion
 *     The resolved Minecraft version/revision to build.
 * @param buildToolsUri
 *     The URI of the BuildTools jar to execute.
 * @param buildToolsIdentity
 *     A stable identifier included in the cache key for BuildTools-based builds.
 */
public record SpigotBuildMetadata(
    String minecraftVersion,
    URI buildToolsUri,
    String buildToolsIdentity
)
{
}
