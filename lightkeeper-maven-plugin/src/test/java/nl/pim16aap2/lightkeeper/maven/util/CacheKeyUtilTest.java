package nl.pim16aap2.lightkeeper.maven.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CacheKeyUtilTest
{
    @Test
    void createCacheKey_shouldGenerateDifferentHashWhenInputChanges()
    {
        // setup
        final List<String> baseParts = List.of("paper", "1.21.11", "sha-a");
        final List<String> changedParts = List.of("paper", "1.21.11", "sha-b");

        // execute
        final String first = CacheKeyUtil.createCacheKey(baseParts);
        final String second = CacheKeyUtil.createCacheKey(changedParts);

        // verify
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createCacheKey_shouldGenerateDeterministicHash()
    {
        // setup
        final List<String> parts = List.of("paper", "1.21.11", "sha-a");

        // execute
        final String first = CacheKeyUtil.createCacheKey(parts);
        final String second = CacheKeyUtil.createCacheKey(parts);

        // verify
        assertThat(first).isEqualTo(second);
    }

    @Test
    void createPaperCacheKey_shouldGenerateDifferentHashWhenJarShaChanges()
    {
        // setup
        final String version = "1.21.11";

        // execute
        final String first = CacheKeyUtil.createPaperCacheKey(version, "sha-a");
        final String second = CacheKeyUtil.createPaperCacheKey(version, "sha-b");

        // verify
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void createSpigotCacheKey_shouldGenerateDifferentHashWhenEnvironmentChanges()
    {
        // setup
        final String version = "1.21.11";
        final String buildToolsIdentity = "buildtools-lastSuccessfulBuild";

        // execute
        final String first = CacheKeyUtil.createSpigotCacheKey(
            version,
            buildToolsIdentity,
            "21",
            "Linux",
            "amd64"
        );
        final String second = CacheKeyUtil.createSpigotCacheKey(
            version,
            buildToolsIdentity,
            "21",
            "Windows 11",
            "amd64"
        );

        // verify
        assertThat(first).isNotEqualTo(second);
    }
}
