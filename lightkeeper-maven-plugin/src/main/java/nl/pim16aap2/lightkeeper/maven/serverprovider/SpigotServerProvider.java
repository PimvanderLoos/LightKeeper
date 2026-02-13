package nl.pim16aap2.lightkeeper.maven.serverprovider;

import lombok.ToString;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.SpigotBuildMetadata;
import nl.pim16aap2.lightkeeper.maven.serverprocess.MinecraftServerProcess;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Server provider that builds Spigot jars via BuildTools and prepares a reusable base server.
 */
@ToString(callSuper = true)
public class SpigotServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "spigot";
    private static final Duration BUILDTOOLS_TIMEOUT = Duration.ofMinutes(30);
    private static final int BUILDTOOLS_OUTPUT_TAIL_SIZE = 80;
    private static final String BUILDTOOLS_LOG_FILE_NAME = "lightkeeper-buildtools.log";

    private final SpigotBuildMetadata spigotBuildMetadata;

    public SpigotServerProvider(
        Log log,
        ServerSpecification serverSpecification,
        SpigotBuildMetadata spigotBuildMetadata)
    {
        super(log, SERVER_NAME, serverSpecification);
        this.spigotBuildMetadata = spigotBuildMetadata;
    }

    @Override
    protected void createBaseServerJar()
        throws MojoExecutionException
    {
        final Path buildToolsJar = jarCacheDirectory().resolve("BuildTools.jar");
        final Path buildToolsWorkDirectory = jarCacheDirectory().resolve("buildtools-work");

        downloadFile(spigotBuildMetadata.buildToolsUri().toString(), buildToolsJar);
        FileUtil.createDirectories(buildToolsWorkDirectory, "BuildTools work directory");

        runBuildTools(buildToolsJar, buildToolsWorkDirectory);

        final Path builtJar = resolveBuiltSpigotJar(jarCacheDirectory(), spigotBuildMetadata.minecraftVersion());
        try
        {
            Files.copy(builtJar, jarCacheFile(), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to copy built Spigot jar from '%s' to '%s'."
                    .formatted(builtJar, jarCacheFile()),
                exception
            );
        }
    }

    @Override
    protected void createBaseServer()
        throws MojoExecutionException
    {
        acceptEula();
        writeServerProperties(createDefaultServerProperties());

        final int maxAttempts = Math.max(1, serverSpecification().serverStartMaxAttempts());

        for (int attempt = 1; attempt <= maxAttempts; ++attempt)
        {
            final MinecraftServerProcess serverProcess = createServerProcess();

            try
            {
                log().info(
                    "LK_SERVER: Starting Spigot base server process (attempt %d/%d)."
                        .formatted(attempt, maxAttempts)
                );
                serverProcess.start(serverSpecification().serverInitTimeoutSeconds());
                log().info("LK_SERVER: Spigot base server started successfully.");
                log().info("LK_SERVER: Stopping Spigot base server process.");
                serverProcess.stop(serverSpecification().serverStopTimeoutSeconds());
                log().info("LK_SERVER: Spigot base server stopped successfully.");
                return;
            }
            catch (MojoExecutionException exception)
            {
                if (attempt >= maxAttempts)
                {
                    throw new MojoExecutionException(
                        "Spigot server failed to start after %d attempt(s).".formatted(maxAttempts),
                        exception
                    );
                }

                log().warn(
                    ("LK_RETRY_STARTUP_PROCESS_ONLY: Spigot startup attempt %d/%d failed. " +
                        "Retrying process startup without re-downloading or rebuilding cache. Cause: %s")
                        .formatted(attempt, maxAttempts, exception.getMessage())
                );
                cleanTransientFilesForRetry();
            }
        }
    }

    protected MinecraftServerProcess createServerProcess()
    {
        return new MinecraftServerProcess(
            baseServerDirectory(),
            baseServerJarFile(),
            serverSpecification().javaExecutablePath(),
            serverSpecification().extraJvmArgs(),
            serverSpecification().memoryMb()
        );
    }

    private void runBuildTools(Path buildToolsJar, Path buildToolsWorkDirectory)
        throws MojoExecutionException
    {
        final Path buildToolsLogFile = buildToolsWorkDirectory.resolve(BUILDTOOLS_LOG_FILE_NAME);
        final List<String> command = new ArrayList<>();
        command.add(serverSpecification().javaExecutablePath());
        final @Nullable String extraJvmArgs = serverSpecification().extraJvmArgs();
        if (extraJvmArgs != null && !extraJvmArgs.isBlank())
            command.addAll(Arrays.asList(extraJvmArgs.split("\\s+")));
        command.add("-jar");
        command.add(buildToolsJar.toString());
        command.add("--rev");
        command.add(spigotBuildMetadata.minecraftVersion());
        command.add("--output-dir");
        command.add(jarCacheDirectory().toString());

        final ProcessBuilder processBuilder = new ProcessBuilder(command)
            .directory(buildToolsWorkDirectory.toFile())
            .redirectErrorStream(true)
            .redirectOutput(buildToolsLogFile.toFile());

        final Process process;
        try
        {
            log().info("Starting Spigot BuildTools process. Output log: " + buildToolsLogFile);
            process = processBuilder.start();
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to start BuildTools process.", exception);
        }

        final boolean finished;
        try
        {
            finished = process.waitFor(BUILDTOOLS_TIMEOUT.toSeconds(), java.util.concurrent.TimeUnit.SECONDS);
        }
        catch (InterruptedException exception)
        {
            Thread.currentThread().interrupt();
            if (process.isAlive())
                process.destroyForcibly();
            throw new MojoExecutionException("Interrupted while waiting for BuildTools to finish.", exception);
        }

        if (!finished)
        {
            if (process.isAlive())
                process.destroyForcibly();
            throw new MojoExecutionException(
                "BuildTools timed out after %s. Tail:%n%s"
                    .formatted(BUILDTOOLS_TIMEOUT, readOutputTail(buildToolsLogFile))
            );
        }

        if (process.exitValue() != 0)
        {
            throw new MojoExecutionException(
                "BuildTools failed with exit code %d. Tail:%n%s"
                    .formatted(process.exitValue(), readOutputTail(buildToolsLogFile))
            );
        }

        log().info("BuildTools completed successfully. Output log: " + buildToolsLogFile);
    }

    private static String readOutputTail(Path outputFile)
    {
        if (Files.notExists(outputFile))
            return "<no BuildTools output available>";

        final Deque<String> tailLines = new ArrayDeque<>(BUILDTOOLS_OUTPUT_TAIL_SIZE);
        try (BufferedReader reader = Files.newBufferedReader(outputFile, StandardCharsets.UTF_8))
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                if (tailLines.size() == BUILDTOOLS_OUTPUT_TAIL_SIZE)
                    tailLines.removeFirst();
                tailLines.addLast(line);
            }
        }
        catch (IOException exception)
        {
            return "<failed to read BuildTools output from '%s': %s>"
                .formatted(outputFile, exception.getMessage());
        }

        if (tailLines.isEmpty())
            return "<BuildTools output was empty>";
        return String.join(System.lineSeparator(), tailLines);
    }

    private static Path resolveBuiltSpigotJar(Path jarCacheDirectory, String minecraftVersion)
        throws MojoExecutionException
    {
        final String expectedJarFileName = "spigot-%s.jar".formatted(minecraftVersion).toLowerCase(Locale.ROOT);

        try (Stream<Path> files = Files.list(jarCacheDirectory))
        {
            final List<Path> candidates = files
                .filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).equals(expectedJarFileName))
                .toList();

            if (candidates.isEmpty())
            {
                throw new MojoExecutionException(
                    "BuildTools did not produce the runtime Spigot jar '%s' in '%s'."
                        .formatted(expectedJarFileName, jarCacheDirectory)
                );
            }

            return candidates.getFirst();
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to inspect BuildTools output directory '%s'."
                    .formatted(jarCacheDirectory),
                exception
            );
        }
    }

    private void cleanTransientFilesForRetry()
        throws MojoExecutionException
    {
        try (Stream<Path> fileStream = Files.walk(baseServerDirectory()))
        {
            fileStream
                .filter(Files::isRegularFile)
                .filter(this::isTransientRetryFile)
                .forEach(this::deleteRetryFile);
        }
        catch (UncheckedIOException exception)
        {
            throw new MojoExecutionException(
                "Failed to delete transient retry file in '%s'."
                    .formatted(baseServerDirectory()),
                exception
            );
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to clean transient files in base server directory '%s' before retry."
                    .formatted(baseServerDirectory()),
                exception
            );
        }
    }

    private boolean isTransientRetryFile(Path path)
    {
        final String fileName = path.getFileName() == null
            ? path.toString()
            : path.getFileName().toString();
        final String lowerCaseName = fileName.toLowerCase(Locale.ROOT);

        if (lowerCaseName.equals("session.lock"))
            return true;

        if (lowerCaseName.endsWith(".lock") || lowerCaseName.endsWith(".lck"))
            return true;

        if (lowerCaseName.endsWith(".pid") || lowerCaseName.endsWith(".sock"))
            return true;

        return lowerCaseName.startsWith("hs_err_pid") && lowerCaseName.endsWith(".log");
    }

    private void deleteRetryFile(Path path)
    {
        try
        {
            Files.deleteIfExists(path);
        }
        catch (IOException exception)
        {
            throw new UncheckedIOException(
                "Failed to delete transient retry file '%s'.".formatted(path),
                exception
            );
        }
    }
}
