package nl.pim16aap2.lightkeeper.maven.util;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Utility for generating deterministic cache keys.
 */
public final class CacheKeyUtil
{
    private static final String SERVER_TYPE_PAPER = "paper";
    private static final String SERVER_TYPE_SPIGOT = "spigot";

    private CacheKeyUtil()
    {
    }

    public static String createPaperCacheKey(String minecraftVersion, String serverJarSha256)
    {
        return createCacheKey(List.of(
            SERVER_TYPE_PAPER,
            requireNonBlank(minecraftVersion, "minecraftVersion"),
            requireNonBlank(serverJarSha256, "serverJarSha256")
        ));
    }

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

    public static String createCacheKey(List<String> parts)
    {
        final StringJoiner joiner = new StringJoiner("|");
        for (final String part : parts)
            joiner.add(requireNonBlank(part, "cacheKeyPart"));
        return HashUtil.sha256(joiner.toString()).toLowerCase(Locale.ROOT);
    }

    private static String requireNonBlank(String value, String fieldName)
    {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(fieldName + " may not be null or blank.");
        return value;
    }
}
