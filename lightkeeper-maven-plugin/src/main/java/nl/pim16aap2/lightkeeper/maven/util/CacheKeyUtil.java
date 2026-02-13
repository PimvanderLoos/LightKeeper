package nl.pim16aap2.lightkeeper.maven.util;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

/**
 * Utility for generating deterministic cache keys.
 */
public final class CacheKeyUtil
{
    private CacheKeyUtil()
    {
    }

    public static String createCacheKey(List<String> parts)
    {
        final StringJoiner joiner = new StringJoiner("|");
        for (final String part : parts)
            joiner.add(part);
        return HashUtil.sha256(joiner.toString()).toLowerCase(Locale.ROOT);
    }
}
