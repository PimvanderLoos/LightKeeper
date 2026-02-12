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
        final List<String> baseParts = List.of("paper", "1.21.11", "42", "21", "linux", "amd64", "agent-a");
        final List<String> changedParts = List.of("paper", "1.21.11", "42", "21", "linux", "amd64", "agent-b");

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
        final List<String> parts = List.of("paper", "1.21.11", "42", "21", "linux", "amd64", "agent-a");

        // execute
        final String first = CacheKeyUtil.createCacheKey(parts);
        final String second = CacheKeyUtil.createCacheKey(parts);

        // verify
        assertThat(first).isEqualTo(second);
    }
}
