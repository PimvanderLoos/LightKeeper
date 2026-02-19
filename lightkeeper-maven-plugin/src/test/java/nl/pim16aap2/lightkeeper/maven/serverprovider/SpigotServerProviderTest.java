package nl.pim16aap2.lightkeeper.maven.serverprovider;

import nl.pim16aap2.lightkeeper.maven.LightkeeperEmbeddedAgent;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.SpigotBuildMetadata;
import nl.pim16aap2.lightkeeper.maven.serverprocess.MinecraftServerProcess;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpigotServerProviderTest
{
    private static final String EMBEDDED_AGENT_SHA256 = resolveEmbeddedAgentSha256();

    private static final SpigotBuildMetadata SPIGOT_BUILD_METADATA = new SpigotBuildMetadata(
        "1.21.11",
        URI.create("https://example.com/BuildTools.jar"),
        "buildtools-identity"
    );

    @Test
    void prepareServer_shouldRetryServerStartupWithoutRebuildingJar(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestSpigotServerProvider provider = createProvider(tempDirectory, 2, 1, false);

        // execute
        provider.prepareServer();

        // verify
        assertThat(provider.createBaseServerJarInvocations()).isEqualTo(1);
        assertThat(provider.createServerProcessInvocations()).isEqualTo(2);
        assertThat(provider.targetServerDirectoryPath().resolve("spigot-1.21.11.jar")).isRegularFile();
    }

    @Test
    void prepareServer_shouldDeleteTransientFilesBeforeRetry(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final TestSpigotServerProvider provider = createProvider(tempDirectory, 2, 1, true);

        // execute
        provider.prepareServer();

        // verify
        final Path baseLockFile = provider.baseServerDirectoryForTests().resolve("world/session.lock");
        final Path targetLockFile = provider.targetServerDirectoryPath().resolve("world/session.lock");
        assertThat(baseLockFile).doesNotExist();
        assertThat(targetLockFile).doesNotExist();
    }

    @Test
    void prepareServer_shouldFailAfterMaxStartupAttemptsWithoutRebuildingJar(@TempDir Path tempDirectory)
    {
        // setup
        final TestSpigotServerProvider provider = createProvider(tempDirectory, 2, 2, false);

        // execute
        final var thrown = assertThatThrownBy(provider::prepareServer);

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("failed to start after 2 attempt(s)");
        assertThat(provider.createBaseServerJarInvocations()).isEqualTo(1);
        assertThat(provider.createServerProcessInvocations()).isEqualTo(2);
    }

    @Test
    void resolveBuiltSpigotJar_shouldSelectRuntimeJarWhenApiJarExists(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        Files.writeString(tempDirectory.resolve("spigot-api-1.21.11-R0.1-SNAPSHOT.jar"), "api");
        final Path runtimeJar = Files.writeString(tempDirectory.resolve("spigot-1.21.11.jar"), "runtime");

        // execute
        final Path resolvedJar = resolveBuiltSpigotJarForTests(tempDirectory, "1.21.11");

        // verify
        assertThat(resolvedJar).isEqualTo(runtimeJar);
    }

    @Test
    void resolveBuiltSpigotJar_shouldThrowWhenRuntimeJarIsMissing(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        Files.writeString(tempDirectory.resolve("spigot-api-1.21.11-R0.1-SNAPSHOT.jar"), "api");

        // execute
        final var thrown = assertThatThrownBy(() -> resolveBuiltSpigotJarForTests(tempDirectory, "1.21.11"));

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("spigot-1.21.11.jar");
    }

    @Test
    void resolveBuiltSpigotJar_shouldThrowWhenOutputDirectoryCannotBeListed(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path notDirectory = Files.writeString(tempDirectory.resolve("not-a-directory.txt"), "content");

        // execute
        final var thrown = assertThatThrownBy(() -> resolveBuiltSpigotJarForTests(notDirectory, "1.21.11"));

        // verify
        thrown.isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("Failed to inspect BuildTools output directory");
    }

    @Test
    void createBaseServerJar_shouldBuildAndCopySpigotJar(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path successfulBuildToolsJar = createFakeBuildToolsJar(tempDirectory, false);
        final TestSpigotJarProvider provider = createJarProvider(tempDirectory, successfulBuildToolsJar);

        // execute
        provider.runCreateBaseServerJar();

        // verify
        assertThat(provider.jarCacheFileForTests()).isRegularFile();
        assertThat(provider.jarCacheFileForTests().getFileName().toString()).isEqualTo("spigot-1.21.11.jar");
    }

    @Test
    void createBaseServerJar_shouldThrowExceptionWhenBuildToolsFails(@TempDir Path tempDirectory)
        throws Exception
    {
        // setup
        final Path failingBuildToolsJar = createFakeBuildToolsJar(tempDirectory, true);
        final TestSpigotJarProvider provider = createJarProvider(tempDirectory, failingBuildToolsJar);

        // execute + verify
        assertThatThrownBy(provider::runCreateBaseServerJar)
            .isInstanceOf(MojoExecutionException.class)
            .hasMessageContaining("BuildTools failed with exit code");
    }

    @Test
    void createServerProcess_shouldCreateMinecraftServerProcessInstance(@TempDir Path tempDirectory)
    {
        // setup
        final TestSpigotJarProvider provider = createJarProvider(tempDirectory, tempDirectory.resolve("unused.jar"));

        // execute
        final MinecraftServerProcess process = provider.createServerProcessForTests();

        // verify
        assertThat(process).isNotNull();
    }

    private static Path resolveBuiltSpigotJarForTests(Path jarCacheDirectory, String minecraftVersion)
        throws Exception
    {
        final Method resolveMethod =
            SpigotServerProvider.class.getDeclaredMethod("resolveBuiltSpigotJar", Path.class, String.class);
        resolveMethod.setAccessible(true);

        try
        {
            return (Path) resolveMethod.invoke(null, jarCacheDirectory, minecraftVersion);
        }
        catch (InvocationTargetException exception)
        {
            final Throwable cause = exception.getCause();
            if (cause instanceof Exception wrappedException)
                throw wrappedException;
            throw exception;
        }
    }

    private TestSpigotServerProvider createProvider(
        Path tempDirectory,
        int maxAttempts,
        int failingAttempts,
        boolean createTransientLockOnFailure)
    {
        final Log log = new SystemStreamLog();
        final ServerSpecification serverSpecification = new ServerSpecification(
            "1.21.11",
            tempDirectory.resolve("jars"),
            tempDirectory.resolve("base"),
            tempDirectory.resolve("work"),
            tempDirectory.resolve("runtime-manifest.json"),
            tempDirectory.resolve("sockets"),
            false,
            30,
            true,
            30,
            true,
            true,
            5,
            5,
            maxAttempts,
            512,
            "java",
            null,
            "cache-key",
            "LightKeeper/Tests",
            EMBEDDED_AGENT_SHA256,
            "test-token",
            1,
            "embedded-agent"
        );

        return new TestSpigotServerProvider(
            log,
            serverSpecification,
            SPIGOT_BUILD_METADATA,
            failingAttempts,
            createTransientLockOnFailure
        );
    }

    private TestSpigotJarProvider createJarProvider(Path tempDirectory, Path buildToolsJar)
    {
        final Log log = new SystemStreamLog();
        final ServerSpecification serverSpecification = new ServerSpecification(
            "1.21.11",
            tempDirectory.resolve("jars"),
            tempDirectory.resolve("base"),
            tempDirectory.resolve("work"),
            tempDirectory.resolve("runtime-manifest.json"),
            tempDirectory.resolve("sockets"),
            false,
            30,
            true,
            30,
            true,
            true,
            5,
            5,
            1,
            512,
            System.getProperty("java.home") + "/bin/java",
            null,
            "cache-key",
            "LightKeeper/Tests",
            EMBEDDED_AGENT_SHA256,
            "test-token",
            1,
            "embedded-agent"
        );

        return new TestSpigotJarProvider(log, serverSpecification, SPIGOT_BUILD_METADATA, buildToolsJar);
    }

    private static String resolveEmbeddedAgentSha256()
    {
        try (var embeddedAgentStream = LightkeeperEmbeddedAgent.openStream())
        {
            return HashUtil.sha256(embeddedAgentStream);
        }
        catch (Exception exception)
        {
            throw new IllegalStateException("Failed to resolve embedded agent SHA-256 for tests.", exception);
        }
    }

    private static Path createFakeBuildToolsJar(Path tempDirectory, boolean fail)
        throws Exception
    {
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new IllegalStateException("JDK compiler is required for this test.");

        final Path sourceDirectory = Files.createDirectories(tempDirectory.resolve("fakebuildtools-src"));
        final Path classDirectory = Files.createDirectories(tempDirectory.resolve("fakebuildtools-classes"));
        final Path sourceFile = sourceDirectory.resolve("FakeBuildTools.java");
        Files.writeString(sourceFile, """
            import java.nio.file.Files;
            import java.nio.file.Path;

            public final class FakeBuildTools {
                public static void main(String[] args) throws Exception {
                    String rev = null;
                    String outputDir = null;
                    for (int i = 0; i < args.length; ++i) {
                        if ("--rev".equals(args[i]) && i + 1 < args.length) rev = args[i + 1];
                        if ("--output-dir".equals(args[i]) && i + 1 < args.length) outputDir = args[i + 1];
                    }
                    if (%s) {
                        System.exit(2);
                        return;
                    }
                    if (rev == null || outputDir == null) {
                        throw new IllegalArgumentException("Missing --rev/--output-dir");
                    }
                    Path outDir = Path.of(outputDir);
                    Files.createDirectories(outDir);
                    Files.writeString(outDir.resolve("spigot-" + rev + ".jar"), "fake-spigot");
                }
            }
            """.formatted(fail ? "true" : "false"), StandardCharsets.UTF_8);

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, StandardCharsets.UTF_8))
        {
            final List<String> options = List.of("-d", classDirectory.toString());
            final boolean success = compiler.getTask(
                null,
                fileManager,
                null,
                options,
                null,
                fileManager.getJavaFileObjects(sourceFile.toFile())
            ).call();
            if (!success)
                throw new IllegalStateException("Failed to compile fake BuildTools test class.");
        }

        final Path jarPath = tempDirectory.resolve(fail ? "BuildTools-fail.jar" : "BuildTools-ok.jar");
        try (OutputStream outputStream = Files.newOutputStream(jarPath);
             JarOutputStream jarOutputStream = new JarOutputStream(outputStream))
        {
            final Path classFile = classDirectory.resolve("FakeBuildTools.class");
            jarOutputStream.putNextEntry(new JarEntry("FakeBuildTools.class"));
            jarOutputStream.write(Files.readAllBytes(classFile));
            jarOutputStream.closeEntry();

            jarOutputStream.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            jarOutputStream.write((
                "Manifest-Version: 1.0\n" +
                    "Main-Class: FakeBuildTools\n\n"
            ).getBytes(StandardCharsets.UTF_8));
            jarOutputStream.closeEntry();
        }

        return jarPath;
    }

    private static final class TestSpigotServerProvider extends SpigotServerProvider
    {
        private final int failingAttempts;
        private final boolean createTransientLockOnFailure;
        private int createBaseServerJarInvocations;
        private int createServerProcessInvocations;

        private TestSpigotServerProvider(
            Log log,
            ServerSpecification serverSpecification,
            SpigotBuildMetadata spigotBuildMetadata,
            int failingAttempts,
            boolean createTransientLockOnFailure)
        {
            super(log, serverSpecification, spigotBuildMetadata);
            this.failingAttempts = failingAttempts;
            this.createTransientLockOnFailure = createTransientLockOnFailure;
        }

        @Override
        protected void createBaseServerJar()
            throws MojoExecutionException
        {
            createBaseServerJarInvocations++;
            try
            {
                Files.createDirectories(jarCacheFile().getParent());
                Files.writeString(jarCacheFile(), "spigot-jar");
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException("Failed to create test server jar.", exception);
            }
        }

        @Override
        protected MinecraftServerProcess createServerProcess()
        {
            createServerProcessInvocations++;
            final boolean failStart = createServerProcessInvocations <= failingAttempts;
            return new ScriptedServerProcess(baseServerDirectory(), failStart, createTransientLockOnFailure);
        }

        int createBaseServerJarInvocations()
        {
            return createBaseServerJarInvocations;
        }

        int createServerProcessInvocations()
        {
            return createServerProcessInvocations;
        }

        Path baseServerDirectoryForTests()
        {
            return baseServerDirectory();
        }
    }

    private static final class ScriptedServerProcess extends MinecraftServerProcess
    {
        private final Path serverDirectory;
        private final boolean failStart;
        private final boolean createTransientLockOnFailure;
        private boolean running;

        private ScriptedServerProcess(Path serverDirectory, boolean failStart, boolean createTransientLockOnFailure)
        {
            super(serverDirectory, serverDirectory.resolve("spigot.jar"), "java", null, 512);
            this.serverDirectory = serverDirectory;
            this.failStart = failStart;
            this.createTransientLockOnFailure = createTransientLockOnFailure;
        }

        @Override
        public void start(int timeoutSeconds)
            throws MojoExecutionException
        {
            if (failStart)
            {
                if (createTransientLockOnFailure)
                    createTransientFile();
                throw new MojoExecutionException("Simulated startup failure");
            }

            running = true;
        }

        @Override
        public void stop(int timeoutSeconds)
            throws MojoExecutionException
        {
            if (!running)
                throw new MojoExecutionException("Process not running.");
            running = false;
        }

        @Override
        public boolean isRunning()
        {
            return running;
        }

        private void createTransientFile()
            throws MojoExecutionException
        {
            final Path lockFile = serverDirectory.resolve("world/session.lock");
            try
            {
                Files.createDirectories(lockFile.getParent());
                Files.writeString(lockFile, "locked");
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException("Failed to create transient lock file.", exception);
            }
        }
    }

    private static final class TestSpigotJarProvider extends SpigotServerProvider
    {
        private final Path fakeBuildToolsJar;

        private TestSpigotJarProvider(
            Log log,
            ServerSpecification serverSpecification,
            SpigotBuildMetadata spigotBuildMetadata,
            Path fakeBuildToolsJar)
        {
            super(log, serverSpecification, spigotBuildMetadata);
            this.fakeBuildToolsJar = fakeBuildToolsJar;
        }

        private void runCreateBaseServerJar()
            throws MojoExecutionException
        {
            createBaseServerJar();
        }

        private Path jarCacheFileForTests()
        {
            return jarCacheFile();
        }

        private MinecraftServerProcess createServerProcessForTests()
        {
            return createServerProcess();
        }

        @Override
        protected void downloadFile(String url, Path targetFile)
            throws MojoExecutionException
        {
            try
            {
                Files.createDirectories(targetFile.getParent());
                Files.copy(fakeBuildToolsJar, targetFile);
            }
            catch (IOException exception)
            {
                throw new MojoExecutionException("Failed to copy fake BuildTools jar.", exception);
            }
        }
    }
}
