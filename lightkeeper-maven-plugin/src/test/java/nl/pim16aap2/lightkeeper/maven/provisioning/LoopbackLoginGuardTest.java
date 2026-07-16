package nl.pim16aap2.lightkeeper.maven.provisioning;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoopbackLoginGuardTest
{
    @Test
    void validate_shouldPassOnDefaultProvisionedConfiguration(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup
        writeServerProperties(serverDirectory, "online-mode=false", "network-compression-threshold=256");
        writeSpigotConfiguration(serverDirectory, "settings:", "  bungeecord: false");
        writePaperGlobalConfiguration(
            serverDirectory,
            "proxies:",
            "  velocity:",
            "    enabled: false",
            "    online-mode: true"
        );

        // execute + verify
        assertThatCode(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .doesNotThrowAnyException();
    }

    @Test
    void validate_shouldPassWhenOptionalConfigurationFilesAreMissing(@TempDir Path serverDirectory)
    {
        // setup — an empty directory: nothing to validate must mean nothing to fail on.

        // execute + verify
        assertThatCode(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .doesNotThrowAnyException();
    }

    @Test
    void validate_shouldFailWhenOnlineModeIsEnabled(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup
        writeServerProperties(serverDirectory, "server-port=25565", "online-mode=TRUE");

        // execute + verify
        assertThatThrownBy(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("online-mode=true")
            .hasMessageContaining("FULL_LOGIN");
    }

    @Test
    void validate_shouldFailWhenSpigotBungeecordForwardingIsEnabled(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup
        writeSpigotConfiguration(serverDirectory, "settings:", "  bungeecord: true # enabled by overlay");

        // execute + verify
        assertThatThrownBy(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("BungeeCord")
            .hasMessageContaining("FULL_LOGIN");
    }

    @Test
    void validate_shouldFailWhenPaperVelocityForwardingIsEnabled(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup
        writePaperGlobalConfiguration(
            serverDirectory,
            "proxies:",
            "  velocity:",
            "    enabled: true",
            "    secret: hunter2"
        );

        // execute + verify
        assertThatThrownBy(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Velocity")
            .hasMessageContaining("FULL_LOGIN");
    }

    @Test
    void validate_shouldFailWhenOnlineModeUsesSpacedAssignment(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup — legal properties spacing around the separator.
        writeServerProperties(serverDirectory, "online-mode = true");

        // execute + verify
        assertThatThrownBy(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("online-mode=true")
            .hasMessageContaining("FULL_LOGIN");
    }

    @Test
    void validate_shouldFailWhenBungeecordUsesCapitalizedYamlBoolean(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup — 'True' is a legal YAML boolean spelling.
        writeSpigotConfiguration(serverDirectory, "settings:", "  bungeecord: True");

        // execute + verify
        assertThatThrownBy(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("BungeeCord")
            .hasMessageContaining("FULL_LOGIN");
    }

    @Test
    void validate_shouldFailWhenVelocityUsesUppercaseYamlBoolean(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup — 'TRUE' is a legal YAML boolean spelling.
        writePaperGlobalConfiguration(
            serverDirectory,
            "proxies:",
            "  velocity:",
            "    enabled: TRUE"
        );

        // execute + verify
        assertThatThrownBy(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Velocity")
            .hasMessageContaining("FULL_LOGIN");
    }

    @Test
    void validate_shouldIgnoreEnabledFlagsOutsideTheVelocitySection(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup — 'enabled: true' belongs to a sibling section that starts after the velocity block ends.
        writePaperGlobalConfiguration(
            serverDirectory,
            "proxies:",
            "  velocity:",
            "    enabled: false",
            "  bungee-cord:",
            "    enabled: true",
            "watchdog:",
            "  enabled: true"
        );

        // execute + verify
        assertThatCode(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .doesNotThrowAnyException();
    }

    @Test
    void validate_shouldIgnoreCommentedOutForwardingSettings(@TempDir Path serverDirectory)
        throws Exception
    {
        // setup
        writeServerProperties(
            serverDirectory, "# online-mode=true", "! online-mode=true", "online-mode=false");
        writeSpigotConfiguration(serverDirectory, "settings:", "  # bungeecord: true");
        writePaperGlobalConfiguration(
            serverDirectory,
            "proxies:",
            "  velocity:",
            "    # enabled: true",
            "    enabled: false"
        );

        // execute + verify
        assertThatCode(() -> LoopbackLoginGuard.validate(serverDirectory, new SystemStreamLog()))
            .doesNotThrowAnyException();
    }

    private static void writeServerProperties(Path serverDirectory, String... lines)
        throws IOException
    {
        Files.write(serverDirectory.resolve("server.properties"), List.of(lines));
    }

    private static void writeSpigotConfiguration(Path serverDirectory, String... lines)
        throws IOException
    {
        Files.write(serverDirectory.resolve("spigot.yml"), List.of(lines));
    }

    private static void writePaperGlobalConfiguration(Path serverDirectory, String... lines)
        throws IOException
    {
        final Path configDirectory = serverDirectory.resolve("config");
        Files.createDirectories(configDirectory);
        Files.write(configDirectory.resolve("paper-global.yml"), List.of(lines));
    }
}
