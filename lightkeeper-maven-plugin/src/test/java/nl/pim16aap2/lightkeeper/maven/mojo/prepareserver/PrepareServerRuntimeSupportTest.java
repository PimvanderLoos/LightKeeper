package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import nl.pim16aap2.lightkeeper.maven.serverprovider.ServerProvider;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PrepareServerRuntimeSupportTest
{
    @Test
    void resolveAgentMetadata_shouldResolveEmbeddedAgentMetadata()
        throws Exception
    {
        // setup
        final PrepareServerRuntimeSupport runtimeSupport = new PrepareServerRuntimeSupport(new SystemStreamLog());

        // execute
        final PrepareServerAgentMetadata metadata = runtimeSupport.resolveAgentMetadata();

        // verify
        assertThat(metadata.cacheIdentity()).startsWith("lightkeeper-agent-spigot.jar:");
        assertThat(metadata.sha256()).matches("[a-f0-9]{64}");
    }

    @Test
    void resolveUdsSocketPath_shouldReturnConfiguredSocketWhenPathFits(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final PrepareServerRuntimeSupport runtimeSupport = new PrepareServerRuntimeSupport(new SystemStreamLog());
        final Path configuredDirectory = tempDirectory.resolve("configured");

        // execute
        final Path socketPath =
            runtimeSupport.resolveUdsSocketPath(configuredDirectory, "abcdef0123456789abcdef0123456789");

        // verify
        assertThat(socketPath.toString()).startsWith(configuredDirectory.toAbsolutePath().toString());
        assertThat(socketPath.getFileName().toString()).isEqualTo("lk-abcdef01.sock");
        assertThat(configuredDirectory).isDirectory();
    }

    @Test
    void resolveUdsSocketPath_shouldWarnWhenConfiguredDirectoryIsWritableByOtherUsers(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Log log = mock(Log.class);
        final Path configuredDirectory = Files.createDirectories(tempDirectory.resolve("configured"));
        Files.setPosixFilePermissions(configuredDirectory, PosixFilePermissions.fromString("rwxrwxrwx"));
        final PrepareServerRuntimeSupport runtimeSupport = new PrepareServerRuntimeSupport(log);

        // execute
        runtimeSupport.resolveUdsSocketPath(configuredDirectory, "abcdef0123456789abcdef0123456789");

        // verify
        verify(log).warn(contains("writable by other users"));
    }

    @Test
    void resolveUdsSocketPath_shouldNotWarnWhenConfiguredDirectoryIsNotWritableByOtherUsers(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Log log = mock(Log.class);
        final Path configuredDirectory = Files.createDirectories(tempDirectory.resolve("configured"));
        Files.setPosixFilePermissions(configuredDirectory, PosixFilePermissions.fromString("rwxr-xr-x"));
        final PrepareServerRuntimeSupport runtimeSupport = new PrepareServerRuntimeSupport(log);

        // execute
        runtimeSupport.resolveUdsSocketPath(configuredDirectory, "abcdef0123456789abcdef0123456789");

        // verify
        verify(log, never()).warn(any(CharSequence.class));
    }

    @Test
    void resolveUdsSocketPath_shouldThrowExceptionWhenConfiguredPathIsTooLong(@TempDir Path tempDirectory)
    {
        // setup
        final PrepareServerRuntimeSupport runtimeSupport = new PrepareServerRuntimeSupport(new SystemStreamLog());
        final Path configuredDirectory = tempDirectory.resolve("very-long-segment-".repeat(7));

        // execute + verify
        assertThatThrownBy(
            () -> runtimeSupport.resolveUdsSocketPath(configuredDirectory, "abcdef0123456789abcdef0123456789"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("exceeding the AF_UNIX limit")
            .hasMessageContaining("lightkeeper.agentSocketDirectory");
        assertThat(configuredDirectory).doesNotExist();
    }

    @Test
    void resolveUdsSocketPath_shouldUseDefaultDirectoryWhenNotConfigured(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path defaultDirectory = tempDirectory.resolve("default");
        final PrepareServerRuntimeSupport runtimeSupport =
            new PrepareServerRuntimeSupport(new SystemStreamLog(), defaultDirectory);

        // execute
        final Path socketPath = runtimeSupport.resolveUdsSocketPath(null, "abcdef0123456789abcdef0123456789");

        // verify
        assertThat(socketPath.toString()).startsWith(defaultDirectory.toAbsolutePath().toString());
        assertThat(socketPath.getFileName().toString()).isEqualTo("lk-abcdef01.sock");
        assertThat(defaultDirectory).isDirectory();
    }

    @Test
    void resolveUdsSocketPath_shouldCreateDefaultDirectoryWithUserOnlyPermissions(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Path defaultDirectory = tempDirectory.resolve("default");
        final PrepareServerRuntimeSupport runtimeSupport =
            new PrepareServerRuntimeSupport(new SystemStreamLog(), defaultDirectory);

        // execute
        runtimeSupport.resolveUdsSocketPath(null, "abcdef0123456789abcdef0123456789");

        // verify
        assertThat(Files.getPosixFilePermissions(defaultDirectory)).containsExactlyInAnyOrder(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE
        );
    }

    @Test
    void resolveUdsSocketPath_shouldRejectDefaultDirectoryAccessibleToOtherUsers(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Path defaultDirectory = tempDirectory.resolve("default");
        Files.createDirectories(defaultDirectory);
        Files.setPosixFilePermissions(defaultDirectory, PosixFilePermissions.fromString("rwxr-x---"));
        final PrepareServerRuntimeSupport runtimeSupport =
            new PrepareServerRuntimeSupport(new SystemStreamLog(), defaultDirectory);

        // execute + verify
        assertThatThrownBy(() -> runtimeSupport.resolveUdsSocketPath(null, "abcdef0123456789abcdef0123456789"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("requires user-only permissions 'rwx------'");
    }

    @Test
    void resolveUdsSocketPath_shouldRejectDefaultDirectoryWithoutOwnerWriteAccess(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        assumePosixFileSystem();
        final Path defaultDirectory = tempDirectory.resolve("default");
        Files.createDirectories(defaultDirectory);
        Files.setPosixFilePermissions(defaultDirectory, PosixFilePermissions.fromString("r-x------"));
        final PrepareServerRuntimeSupport runtimeSupport =
            new PrepareServerRuntimeSupport(new SystemStreamLog(), defaultDirectory);

        // execute + verify
        assertThatThrownBy(() -> runtimeSupport.resolveUdsSocketPath(null, "abcdef0123456789abcdef0123456789"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("has permissions 'r-x------'")
            .hasMessageContaining("requires user-only permissions 'rwx------'");
    }

    @Test
    void resolveUdsSocketPath_shouldThrowExceptionWhenDefaultPathIsTooLong(@TempDir Path tempDirectory)
    {
        // setup
        final Path defaultDirectory = tempDirectory.resolve("very-long-segment-".repeat(7));
        final PrepareServerRuntimeSupport runtimeSupport =
            new PrepareServerRuntimeSupport(new SystemStreamLog(), defaultDirectory);

        // execute + verify
        assertThatThrownBy(() -> runtimeSupport.resolveUdsSocketPath(null, "abcdef0123456789abcdef0123456789"))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("exceeding the AF_UNIX limit");
        assertThat(defaultDirectory).doesNotExist();
    }

    @Test
    void resolveDefaultSocketDirectory_shouldPreferXdgRuntimeDirectoryWhenItExists(@TempDir Path tempDirectory)
    {
        // setup
        final String xdgRuntimeDirectory = tempDirectory.toString();

        // execute
        final Path defaultDirectory =
            PrepareServerRuntimeSupport.resolveDefaultSocketDirectory(xdgRuntimeDirectory, "pim", "/unused-tmp");

        // verify
        assertThat(defaultDirectory).isEqualTo(tempDirectory.resolve("lightkeeper").toAbsolutePath());
    }

    @Test
    void resolveDefaultSocketDirectory_shouldFallBackToTmpDirectoryWhenXdgRuntimeDirectoryIsMissing(
        @TempDir Path tempDirectory)
    {
        // setup
        final String missingXdgRuntimeDirectory = tempDirectory.resolve("missing").toString();
        final Path tmpDirectory = tempDirectory.resolve("tmp");

        // execute
        final Path defaultDirectory = PrepareServerRuntimeSupport.resolveDefaultSocketDirectory(
            missingXdgRuntimeDirectory,
            "pim",
            tmpDirectory.toString()
        );

        // verify
        assertThat(defaultDirectory).isEqualTo(tmpDirectory.resolve("lightkeeper-pim").toAbsolutePath());
    }

    @Test
    void resolveDefaultSocketDirectory_shouldFallBackToTmpDirectoryWhenXdgRuntimeDirectoryIsBlank(
        @TempDir Path tempDirectory)
    {
        // setup
        final Path tmpDirectory = tempDirectory.resolve("tmp");

        // execute
        final Path unsetResult =
            PrepareServerRuntimeSupport.resolveDefaultSocketDirectory(null, "pim", tmpDirectory.toString());
        final Path blankResult =
            PrepareServerRuntimeSupport.resolveDefaultSocketDirectory(" ", "pim", tmpDirectory.toString());

        // verify
        assertThat(unsetResult).isEqualTo(tmpDirectory.resolve("lightkeeper-pim").toAbsolutePath());
        assertThat(blankResult).isEqualTo(tmpDirectory.resolve("lightkeeper-pim").toAbsolutePath());
    }

    @Test
    void resolveDefaultSocketDirectory_shouldSanitizeUserName(@TempDir Path tempDirectory)
    {
        // setup
        final Path tmpDirectory = tempDirectory.resolve("tmp");

        // execute
        final Path sanitizedResult = PrepareServerRuntimeSupport.resolveDefaultSocketDirectory(
            null,
            "DOMAIN\\We ird/User",
            tmpDirectory.toString()
        );
        final Path unknownUserResult =
            PrepareServerRuntimeSupport.resolveDefaultSocketDirectory(null, null, tmpDirectory.toString());

        // verify
        assertThat(sanitizedResult).isEqualTo(tmpDirectory.resolve("lightkeeper-DOMAIN_We_ird_User").toAbsolutePath());
        assertThat(unknownUserResult).isEqualTo(tmpDirectory.resolve("lightkeeper-user").toAbsolutePath());
    }

    private static void assumePosixFileSystem()
    {
        assumeTrue(
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
            "POSIX file permissions are not supported on this file system."
        );
    }

    @Test
    void writeRuntimeManifest_shouldThrowExceptionWhenParentPathIsAFile(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path parentFile = Files.writeString(tempDirectory.resolve("blocked-parent"), "x");
        final Path manifestPath = parentFile.resolve("runtime-manifest.json");
        final RuntimeManifest runtimeManifest = new RuntimeManifest(
            "paper",
            "1.21.11",
            116L,
            "cache-key",
            tempDirectory.resolve("server").toString(),
            tempDirectory.resolve("server/paper.jar").toString(),
            512,
            tempDirectory.resolve("socket.sock").toString(),
            "auth-token",
            null,
            null,
            1,
            "no-agent",
            null,
            List.of()
        );

        // execute + verify
        assertThatThrownBy(() -> PrepareServerRuntimeSupport.writeRuntimeManifest(runtimeManifest, manifestPath))
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("not a directory");
    }

    @Test
    void createRuntimeManifest_shouldMapAllWorldInputSpecsToProvisionedWorlds(@TempDir Path tempDirectory)
    {
        // setup
        final PrepareServerMojo mojo = new PrepareServerMojo();
        final WorldInputSpec startupWorld = new WorldInputSpec(
            "startup-world",
            WorldInputSpec.SourceType.FOLDER,
            tempDirectory.resolve("startup-world"),
            true,
            true,
            "NORMAL",
            "NORMAL",
            0L
        );
        final WorldInputSpec templateWorld = new WorldInputSpec(
            "template-world",
            WorldInputSpec.SourceType.FOLDER,
            tempDirectory.resolve("template-world"),
            true,
            false,
            "NETHER",
            "FLAT",
            99L
        );
        final ServerProvider serverProvider = mock(ServerProvider.class);
        when(serverProvider.targetJarFilePath()).thenReturn(tempDirectory.resolve("paper.jar"));
        final PrepareServerAgentMetadata agentMetadata = new PrepareServerAgentMetadata("abc123", "agent-cache-id");

        // execute
        final RuntimeManifest runtimeManifest = mojo.createRuntimeManifest(
            "paper",
            "1.21.11",
            116L,
            "cache-key",
            tempDirectory.resolve("server"),
            serverProvider,
            768,
            tempDirectory.resolve("socket.sock"),
            "auth-token",
            agentMetadata,
            1,
            List.of(startupWorld, templateWorld)
        );

        // verify
        assertThat(runtimeManifest.provisionedWorlds()).hasSize(2);
        final RuntimeManifest.ProvisionedWorld provisionedStartupWorld = runtimeManifest.provisionedWorlds().get(0);
        assertThat(provisionedStartupWorld.name()).isEqualTo("startup-world");
        assertThat(provisionedStartupWorld.environment()).isEqualTo("NORMAL");
        assertThat(provisionedStartupWorld.worldType()).isEqualTo("NORMAL");
        assertThat(provisionedStartupWorld.seed()).isEqualTo(0L);
        assertThat(provisionedStartupWorld.loadOnStartup()).isTrue();
        final RuntimeManifest.ProvisionedWorld provisionedTemplateWorld = runtimeManifest.provisionedWorlds().get(1);
        assertThat(provisionedTemplateWorld.name()).isEqualTo("template-world");
        assertThat(provisionedTemplateWorld.environment()).isEqualTo("NETHER");
        assertThat(provisionedTemplateWorld.worldType()).isEqualTo("FLAT");
        assertThat(provisionedTemplateWorld.seed()).isEqualTo(99L);
        assertThat(provisionedTemplateWorld.loadOnStartup()).isFalse();
    }
}
