package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdeRuntimeSafetyTest
{
    @Test
    @Timeout(15)
    void acquire_shouldRejectLeaseHeldByAnotherProcess(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final String testClassPath = System.getProperty(
            "surefire.test.class.path",
            System.getProperty("java.class.path")
        );
        final Process process = new ProcessBuilder(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-cp",
            testClassPath,
            LockHolderMain.class.getName(),
            tempDirectory.toString()
        ).redirectErrorStream(true).start();

        try
        {
            final BufferedReader processOutput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            assertThat(processOutput.readLine()).isEqualTo("locked");

            // execute + verify
            assertThatThrownBy(() -> IdeRuntimeLock.acquire(tempDirectory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("busy");
            process.getOutputStream().close();
            assertThat(process.waitFor(10, TimeUnit.SECONDS)).isTrue();
            assertThat(process.exitValue()).isZero();
            assertThatCode(() -> IdeRuntimeLock.acquire(tempDirectory).close()).doesNotThrowAnyException();
        }
        finally
        {
            process.destroyForcibly();
        }
    }

    @Test
    void acquire_shouldRejectConcurrentLeaseAndAllowReuseAfterClose(@TempDir Path tempDirectory)
    {
        // setup
        final IdeRuntimeLock first = IdeRuntimeLock.acquire(tempDirectory);

        // execute + verify
        assertThatThrownBy(() -> IdeRuntimeLock.acquire(tempDirectory))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("busy")
            .hasMessageContaining("Close the running test/server");
        first.close();
        assertThatCode(() -> IdeRuntimeLock.acquire(tempDirectory).close()).doesNotThrowAnyException();
    }

    @Test
    void validate_shouldAcceptIntactRuntimeAndMissingCleanedSourceInput(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final RuntimeFixture fixture = createFixture(tempDirectory);
        Files.delete(fixture.sourceInput());

        // execute
        RuntimeManifestValidator.validateForRuntimeStartup(fixture.manifest(), RuntimeProtocol.VERSION);
        IdeRuntimeValidator.validate(fixture.discovery(), tempDirectory, fixture.manifest());

        // verify
        assertThat(Path.of(fixture.manifest().udsSocketPath()).getParent()).isDirectory();
    }

    @Test
    void validate_shouldRejectChangedPreparedArtifact(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final RuntimeFixture fixture = createFixture(tempDirectory);
        Files.writeString(Path.of(fixture.manifest().agentJar()), "tampered-agent");

        // execute + verify
        assertThatThrownBy(() ->
            IdeRuntimeValidator.validate(fixture.discovery(), tempDirectory, fixture.manifest()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("prepared artifact")
            .hasMessageContaining("changed")
            .hasMessageContaining("lightkeeper.ide=true");
    }

    @Test
    void validate_shouldRejectChangedExistingSourceInput(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final RuntimeFixture fixture = createFixture(tempDirectory);
        Files.writeString(fixture.sourceInput(), "new-build");

        // execute + verify
        assertThatThrownBy(() ->
            IdeRuntimeValidator.validate(fixture.discovery(), tempDirectory, fixture.manifest()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("setup input")
            .hasMessageContaining("changed")
            .hasMessageContaining("Rebuild changed artifacts");
    }

    private static RuntimeFixture createFixture(Path moduleDirectory)
        throws Exception
    {
        final Path preparationDirectory = IdeRuntimePaths.preparationsDirectory(moduleDirectory).resolve("fingerprint");
        final Path serverDirectory = Files.createDirectories(preparationDirectory.resolve("server"));
        final Path serverJar = Files.writeString(serverDirectory.resolve("paper.jar"), "server");
        final Path agentJar = serverDirectory.resolve("plugins/lightkeeper-agent-spigot.jar");
        Files.createDirectories(agentJar.getParent());
        Files.writeString(agentJar, "agent");
        final Path sourceInput = Files.createDirectories(moduleDirectory.resolve("target"))
            .resolve("plugin.jar");
        Files.writeString(sourceInput, "build");

        final RuntimeManifest manifest = new RuntimeManifest(
            "paper",
            "1.21.11",
            116L,
            "cache",
            serverDirectory.toString(),
            serverJar.toString(),
            1024,
            preparationDirectory.resolve("socket/lk.sock").toString(),
            "token",
            agentJar.toString(),
            IdeRuntimeArtifactHasher.sha256(agentJar),
            RuntimeProtocol.VERSION,
            "agent-cache",
            null,
            List.of()
        );
        final Path manifestPath = preparationDirectory.resolve("runtime-manifest.json");
        new RuntimeManifestWriter().write(manifest, manifestPath);
        final IdeRuntimeDiscovery discovery = new IdeRuntimeDiscovery(
            IdeRuntimeDiscovery.SCHEMA_VERSION,
            moduleDirectory.toRealPath().toString(),
            "prepare-paper",
            "paper",
            manifestPath.toString(),
            "fingerprint"
        );
        final IdeRuntimeProvenance provenance = new IdeRuntimeProvenance(
            IdeRuntimeProvenance.SCHEMA_VERSION,
            moduleDirectory.toRealPath().toString(),
            "prepare-paper",
            "paper",
            "fingerprint",
            RuntimeProtocol.VERSION,
            List.of(
                artifact(serverJar),
                artifact(agentJar)
            ),
            List.of(artifact(sourceInput))
        );
        new IdeRuntimeProvenanceWriter().write(
            provenance,
            preparationDirectory.resolve(IdeRuntimePaths.PROVENANCE_FILE_NAME)
        );
        return new RuntimeFixture(discovery, manifest, sourceInput);
    }

    private static IdeRuntimeProvenance.Artifact artifact(Path path)
        throws Exception
    {
        return new IdeRuntimeProvenance.Artifact(
            path.toAbsolutePath().normalize().toString(),
            IdeRuntimeArtifactHasher.sha256(path)
        );
    }

    private record RuntimeFixture(
        IdeRuntimeDiscovery discovery,
        RuntimeManifest manifest,
        Path sourceInput)
    {
    }

    /** Child-process entry point used to prove that the IDE lease is process-wide. */
    public static final class LockHolderMain
    {
        private LockHolderMain()
        {
        }

        /**
         * Acquires the requested module lease until the parent closes standard input.
         *
         * @param arguments One module-directory argument.
         * @throws Exception When the lock cannot be acquired or process I/O fails.
         */
        public static void main(String[] arguments)
            throws Exception
        {
            try (IdeRuntimeLock ignored = IdeRuntimeLock.acquire(Path.of(arguments[0])))
            {
                System.out.println("locked");
                System.out.flush();
                System.in.read();
            }
        }
    }
}
