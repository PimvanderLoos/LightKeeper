package nl.pim16aap2.lightkeeper.provisioning.util;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Utility for generating deterministic cache keys.
 */
public final class CacheKeyUtil
{
    /** Platform discriminator for Paper cache inputs. */
    private static final String SERVER_TYPE_PAPER = "paper";
    /** Platform discriminator for Spigot cache inputs. */
    private static final String SERVER_TYPE_SPIGOT = "spigot";

    private CacheKeyUtil()
    {
    }

    /**
     * Identifies a resolved Paper server JAR by version and content.
     *
     * @param minecraftVersion
     *     Resolved Minecraft version.
     * @param serverJarSha256
     *     Expected JAR checksum.
     * @return Deterministic cache key.
     */
    public static String createPaperCacheKey(String minecraftVersion, String serverJarSha256)
    {
        return createCacheKey(List.of(
            SERVER_TYPE_PAPER,
            requireNonBlank(minecraftVersion, "minecraftVersion"),
            requireNonBlank(serverJarSha256, "serverJarSha256")
        ));
    }

    /**
     * Identifies a Spigot build by revision, BuildTools, Java and platform.
     *
     * @param minecraftVersion
     *     Resolved Minecraft revision.
     * @param buildToolsIdentity
     *     BuildTools artifact identity.
     * @param javaSpecificationVersion
     *     Java language/runtime generation.
     * @param osName
     *     Operating system name.
     * @param osArch
     *     Operating system architecture.
     * @return Deterministic cache key.
     */
    public static String createSpigotCacheKey(
        String minecraftVersion,
        String buildToolsIdentity,
        String javaSpecificationVersion,
        String osName,
        String osArch)
    {
        return createCacheKey(List.of(
            SERVER_TYPE_SPIGOT,
            requireNonBlank(minecraftVersion, "minecraftVersion"),
            requireNonBlank(buildToolsIdentity, "buildToolsIdentity"),
            requireNonBlank(javaSpecificationVersion, "javaSpecificationVersion"),
            requireNonBlank(osName, "osName"),
            requireNonBlank(osArch, "osArch")
        ));
    }

    /**
     * Hashes ordered, nonblank preparation inputs into one cache identity.
     *
     * @param parts
     *     Ordered identity components.
     * @return Deterministic cache key.
     */
    public static String createCacheKey(List<String> parts)
    {
        final StringJoiner joiner = new StringJoiner("|");
        for (final String part : parts)
        {
            joiner.add(requireNonBlank(part, "cacheKeyPart"));
        }
        return HashUtil.sha256(joiner.toString()).toLowerCase(Locale.ROOT);
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        if (value == null || value.isBlank())
        {
            throw new IllegalArgumentException(fieldName + " may not be null or blank.");
        }
        return value;
    }
}
