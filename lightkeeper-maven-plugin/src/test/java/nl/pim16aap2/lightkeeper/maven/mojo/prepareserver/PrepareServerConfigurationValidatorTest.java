package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PrepareServerConfigurationValidatorTest
{
    private static final PrepareServerConfigurationValidator VALIDATOR =
        new PrepareServerConfigurationValidator(List.of("paper", "spigot"));

    @Test
    void normalizeServerType_shouldNormalizeConfiguredType()
    {
        // setup
        final String configuredType = "  PaPeR ";

        // execute
        final String normalizedType = VALIDATOR.normalizeServerType(configuredType);

        // verify
        assertThat(normalizedType).isEqualTo("paper");
    }

    @Test
    void validateConfiguration_shouldAcceptValidConfiguration()
        throws Exception
    {
        // setup
        final String serverType = "paper";

        // execute
        VALIDATOR.validateConfiguration(serverType, "LightKeeper/Test", 1, 0, 0, "-Dfoo=bar");

        // verify
    }

    @Test
    void validateConfiguration_shouldThrowExceptionWhenServerTypeIsUnsupported()
    {
        // setup
        final String unsupportedType = "vanilla";

        // execute + verify
        assertThatThrownBy(() -> VALIDATOR.validateConfiguration(
            unsupportedType,
            "LightKeeper/Test",
            1,
            0,
            0,
            null
        ))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Unsupported server type");
    }

    @Test
    void validateConfiguration_shouldThrowExceptionWhenUserAgentIsBlank()
    {
        // setup
        final String userAgent = " ";

        // execute + verify
        assertThatThrownBy(() -> VALIDATOR.validateConfiguration("paper", userAgent, 1, 0, 0, null))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("lightkeeper.userAgent");
    }
}
