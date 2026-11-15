package nl.pim16aap2.lightkeeper.provisioning.serverprovider;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import nl.pim16aap2.lightkeeper.provisioning.LightkeeperEmbeddedAgent;
import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import nl.pim16aap2.lightkeeper.provisioning.ServerSpecification;
import nl.pim16aap2.lightkeeper.provisioning.util.FileUtil;
import nl.pim16aap2.lightkeeper.provisioning.util.HashUtil;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Represents a provider for a specific type of server.
 * <p>
 * This class is the base for the canonical Paper server preparation flow.
 */
@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
@ToString
public abstract class ServerProvider
{
    public static final String EULA_FILE_NAME = "eula.txt";
    private static final Pattern SPIGOT_SETTINGS_HEADER_PATTERN = Pattern.compile("^(\\s*)settings:\\s*$");
    private static final Pattern SPIGOT_WATCHDOG_TIMEOUT_PATTERN = Pattern.compile("^(\\s*)timeout-time:\\s*\\d+\\s*$");
    private static final int SPIGOT_WATCHDOG_TIMEOUT_SECONDS = 600;

    private final System.Logger log;

    /**
     * The name of the server type.
     *
     * @return the name of the server type.
     */
    @Getter
    private final String name;

    /**
     * The specification for the server to be prepared.
     */
    private final ServerSpecification serverSpecification;

    /**
     * The resolved base directory where the server files are cached.
     */
    private final Path baseServerDirectory;

    /**
     * The resolved directory where the server JAR files are cached.
     */
    private final Path jarCacheDirectory;

    /**
     * The resolved directory where the server will run and store its data.
     */
    private final Path targetServerDirectory;

    /**
     * The resolved path to the target JAR file for this server type.
     */
    private final Path targetJarFile;

    /**
     * The resolved path to the server JAR file in the cache.
     */
    private final Path jarCacheFile;

    /**
     * The resolved path to the jar file in the base server directory.
     */
    private final Path baseServerJarFile;

    protected ServerProvider(System.Logger log, String name, ServerSpecification serverSpecification)
    {
        this.log = log;
        this.name = name;
        this.serverSpecification = serverSpecification;

        if (name.isBlank())
            throw new IllegalArgumentException("Server name cannot be null or blank.");

        this.baseServerDirectory = resolveVersionedDirectory(
            serverSpecification.serverVersion(),
            serverSpecification.baseServerCacheDirectoryRoot(),
            serverSpecification.versionedCacheDirectories()
        );

        this.jarCacheDirectory = resolveVersionedDirectory(
            serverSpecification.serverVersion(),
            serverSpecification.jarCacheDirectoryRoot(),
            serverSpecification.versionedCacheDirectories()
        );

        this.targetServerDirectory = resolveVersionedDirectory(
            serverSpecification.serverVersion(),
            serverSpecification.serverWorkDirectoryRoot(),
            serverSpecification.versionedCacheDirectories()
        );

        final String outputJarFileName = getOutputJarFileName();
        this.targetJarFile = this.targetServerDirectory.resolve(outputJarFileName);
        this.jarCacheFile = this.jarCacheDirectory.resolve(outputJarFileName);
        this.baseServerJarFile = this.baseServerDirectory.resolve(outputJarFileName);

    }

    /**
     * Evaluates JAR expiry after shared cache ownership has been acquired.
     *
     * @return Whether the current cache contents require rebuilding.
     */
    protected final boolean shouldRecreateJar()
    {
        return shouldBeRecreated(
            serverSpecification.forceRebuildJar(),
            serverSpecification.jarCacheExpiryDays(),
            jarCacheFile
        );
    }

    /**
     * Evaluates base expiry/version after shared cache ownership has been acquired.
     *
     * @return Whether the current base server requires initialization.
     */
    protected final boolean shouldRecreateBaseServer()
    {
        return shouldBeRecreated(
            serverSpecification.forceRecreateBaseServer(),
            serverSpecification.baseServerCacheExpiryDays(),
            baseServerDirectory.resolve(EULA_FILE_NAME)
        ) || !baseServerVersionMatches();
    }

    /**
     * Checks whether the base server version matches the expected version.
     * <p>
     * If a base server was created for version {@code X} but the expected version is {@code Y} where {@code X != Y},
     * this method will return {@code false}, as the base server is not compatible with the expected version.
     *
     * @return {@code true} if the base server version matches the expected version,
     */
    protected boolean baseServerVersionMatches()
    {
        if (Files.notExists(baseServerJarFile()))
        {
            log.log(System.Logger.Level.INFO, "Expected JAR file '" + baseServerJarFile() + "' does not exist.");
            return false;
        }
        return true;
    }

    /**
     * Gets the file name of the output JAR file for this server type.
     * <p>
     * For Paper this is typically {@code paper-<version>.jar}.
     *
     * @return The file name of the output JAR file.
     */
    protected String getOutputJarFileName()
    {
        return "%s-%s.jar".formatted(name(), serverSpecification().serverVersion());
    }

    /**
     * Accepts the EULA for the server by creating a file named {@code eula.txt} in the base server directory with the
     * content {@code eula=true}.
     *
     * @throws ProvisioningException
     *     If the EULA file already exists or could not be created.
     */
    protected void acceptEula()
        throws ProvisioningException
    {
        final Path eulaFile = baseServerDirectory().resolve(EULA_FILE_NAME);
        try
        {
            log.log(System.Logger.Level.INFO, "Creating EULA file at " + eulaFile);
            Files.writeString(eulaFile, "eula=true", StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e)
        {
            throw new ProvisioningException("Failed to create EULA file at " + eulaFile, e);
        }
    }

    /**
     * Writes the server properties to the base server directory.
     *
     * @param properties
     *     The properties to write to the server properties file.
     * @throws ProvisioningException
     *     If the server properties file already exists or could not be created.
     */
    protected void writeServerProperties(String properties)
        throws ProvisioningException
    {
        final Path serverPropertiesFile = baseServerDirectory().resolve("server.properties");
        try
        {
            log.log(System.Logger.Level.INFO, "Writing server properties to " + serverPropertiesFile);
            Files.writeString(serverPropertiesFile, properties, StandardOpenOption.CREATE_NEW);
        }
        catch (IOException e)
        {
            throw new ProvisioningException("Failed to write server properties to " + serverPropertiesFile, e);
        }
    }

    /**
     * Creates the default server properties content for an offline-mode integration server.
     *
     * @return The server properties content.
     *
     * @throws ProvisioningException
     *     If no free local TCP port can be reserved.
     */
    protected String createDefaultServerProperties()
    {
        return """
            online-mode=false
            enable-query=true
            query.port=25565
            server-port=25565
            enable-rcon=false
            """;
    }

    /**
     * Configures the watchdog timeout in the generated Spigot configuration.
     * <p>
     * Integration world generation can exceed the default watchdog threshold on slower systems. Raising this limit
     * avoids premature server shutdowns during integration tests while keeping watchdog protection enabled.
     *
     * @throws ProvisioningException
     *     If the Spigot configuration file exists but cannot be read or updated.
     */
    protected void configureSpigotWatchdogTimeout()
        throws ProvisioningException
    {
        final Path spigotConfigurationFile = baseServerDirectory().resolve("spigot.yml");
        if (Files.notExists(spigotConfigurationFile))
        {
            log().log(System.Logger.Level.INFO,
                "Skipping Spigot watchdog configuration because '%s' does not exist."
                    .formatted(spigotConfigurationFile)
            );
            return;
        }

        final List<String> lines;
        try
        {
            lines = Files.readAllLines(spigotConfigurationFile, StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to read Spigot configuration from '%s'."
                    .formatted(spigotConfigurationFile),
                exception
            );
        }

        int settingsLineIndex = -1;
        String settingsLineIndentation = "";
        boolean timeoutUpdated = false;
        for (int index = 0; index < lines.size(); ++index)
        {
            final String line = lines.get(index);
            final Matcher timeoutMatcher = SPIGOT_WATCHDOG_TIMEOUT_PATTERN.matcher(line);
            if (timeoutMatcher.matches())
            {
                lines.set(index, timeoutMatcher.group(1) + "timeout-time: " + SPIGOT_WATCHDOG_TIMEOUT_SECONDS);
                timeoutUpdated = true;
                break;
            }

            final Matcher settingsMatcher = SPIGOT_SETTINGS_HEADER_PATTERN.matcher(line);
            if (settingsMatcher.matches())
            {
                settingsLineIndex = index;
                settingsLineIndentation = settingsMatcher.group(1);
            }
        }

        if (!timeoutUpdated)
        {
            if (settingsLineIndex >= 0)
            {
                lines.add(settingsLineIndex + 1, settingsLineIndentation + "  timeout-time: " +
                    SPIGOT_WATCHDOG_TIMEOUT_SECONDS);
            }
            else if (!lines.isEmpty() && !lines.getLast().isBlank())
            {
                lines.add("");
                lines.add("settings:");
                lines.add("  timeout-time: " + SPIGOT_WATCHDOG_TIMEOUT_SECONDS);
            }
        }

        try
        {
            Files.write(spigotConfigurationFile, lines, StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to write Spigot configuration to '%s'."
                    .formatted(spigotConfigurationFile),
                exception
            );
        }

        log().log(System.Logger.Level.INFO,
            "Configured Spigot watchdog timeout to %d second(s) in '%s'."
                .formatted(SPIGOT_WATCHDOG_TIMEOUT_SECONDS, spigotConfigurationFile)
        );
    }

    /**
     * Copies a JAR file from {@link #jarCacheFile()} to {@link #targetJarFile()}.
     *
     * @throws ProvisioningException
     *     If the JAR file could not be copied from the cache to the base server directory.
     */
    protected void copyJarFromCacheToBaseServer()
        throws ProvisioningException
    {
        try
        {
            Files.copy(jarCacheFile(), baseServerJarFile(), StandardCopyOption.REPLACE_EXISTING);
            log().log(System.Logger.Level.INFO, "Copied " + jarCacheFile() + " to " + baseServerJarFile());
        }
        catch (IOException exception)
        {
            throw new ProvisioningException("Failed to copy JAR file from cache", exception);
        }
    }

    /**
     * Prepares the server for use, reporting actual JAR/base-cache reuse or refresh and elapsed preparation time.
     */
    public final void prepareServer()
        throws ProvisioningException
    {
        log().log(System.Logger.Level.INFO, "LK_PHASE: Waiting for shared server-cache ownership.");
        try (ServerCacheLease ignored = ServerCacheLease.acquire(
            serverSpecification.jarCacheDirectoryRoot(),
            serverSpecification.baseServerCacheDirectoryRoot(),
            Duration.ofMinutes(5)
        ))
        {
            prepareWithOwnedCaches();
        }
    }

    private void prepareWithOwnedCaches()
    {
        final long started = System.nanoTime();
        pruneUnusedCacheDirectoriesIfConfigured();
        final boolean recreateJar = shouldRecreateJar();
        final boolean recreateBase = shouldRecreateBaseServer();

        if (recreateJar)
        {
            log().log(System.Logger.Level.INFO,
                "LK_PHASE: Server JAR cache refresh required; downloading/building server.");
            FileUtil.cleanDirectory(jarCacheDirectory(), "jar cache directory");
            createBaseServerJar();

            // The jar file can be updated without recreating the base server directory.
            if (!recreateBase)
            {
                copyJarFromCacheToBaseServer();
            }
        }
        else
        {
            log().log(System.Logger.Level.INFO, "LK_PHASE: Reusing server JAR cache.");
        }

        if (recreateBase)
        {
            log().log(System.Logger.Level.INFO, "LK_PHASE: Base-server cache refresh required; bootstrapping server.");
            FileUtil.cleanDirectory(baseServerDirectory(), "base server directory");
            copyJarFromCacheToBaseServer();
            createBaseServer();
        }
        else
        {
            log().log(System.Logger.Level.INFO, "LK_PHASE: Reusing prepared base-server cache.");
        }

        configureSpigotWatchdogTimeout();

        log().log(System.Logger.Level.INFO, "LK_PHASE: Creating test server and installing LightKeeper agent.");
        FileUtil.cleanDirectory(targetServerDirectory(), "target server directory");
        createTargetServer();
        installEmbeddedAgentJar();
        log().log(System.Logger.Level.INFO, "LK_PHASE: Server files ready ("
            + java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) + " ms).");
    }

    /**
     * Creates the same provider with durable runtime output locations while retaining all resolved server inputs.
     *
     * @param serverWorkDirectoryRoot
     *     Root directory for the prepared runtime.
     * @param runtimeManifestPath
     *     Runtime manifest destination.
     * @return A provider using the supplied runtime output locations.
     */
    public final ServerProvider withRuntimeLocations(Path serverWorkDirectoryRoot, Path runtimeManifestPath)
    {
        return recreate(serverSpecification.withRuntimeLocations(serverWorkDirectoryRoot, runtimeManifestPath));
    }

    /**
     * Recreates this resolved provider with a copied server specification.
     *
     * @param specification
     *     Copied server specification.
     * @return The recreated provider.
     */
    protected abstract ServerProvider recreate(ServerSpecification specification);

    private void pruneUnusedCacheDirectoriesIfConfigured()
    {
        if (!serverSpecification().cleanupUnusedCacheDirectories())
        {
            log().log(System.Logger.Level.INFO, "Unused cache directory cleanup disabled.");
            return;
        }

        pruneUnusedCacheDirectoriesFor(jarCacheDirectory(), serverSpecification().jarCacheExpiryDays(), "jar cache");
        pruneUnusedCacheDirectoriesFor(
            baseServerDirectory(),
            serverSpecification().baseServerCacheExpiryDays(),
            "base server cache"
        );
    }

    private void pruneUnusedCacheDirectoriesFor(Path currentDirectory, int expiryDays, String cacheKind)
    {
        final FileUtil.PruneResult pruneResult;
        try
        {
            pruneResult = FileUtil.pruneSiblingDirectoriesOlderThan(currentDirectory, expiryDays);
        }
        catch (ProvisioningException exception)
        {
            log().log(System.Logger.Level.WARNING,
                "Failed to enumerate unused %s directories for cleanup under '%s'. Cause: %s"
                    .formatted(cacheKind, currentDirectory.getParent(), exception.getMessage())
            );
            return;
        }

        for (final Path deletedDirectory : pruneResult.deletedDirectories())
        {
            log().log(
                System.Logger.Level.INFO,
                "Deleted unused %s directory: '%s'.".formatted(cacheKind, deletedDirectory)
            );
        }

        for (final Path failedDirectory : pruneResult.failedDirectories())
        {
            log().log(
                System.Logger.Level.WARNING,
                "Failed to delete unused %s directory: '%s'. Keeping directory.".formatted(cacheKind, failedDirectory)
            );
        }
    }

    /**
     * Creates the base server JAR file.
     * <p>
     * When this method is called, {@link #jarCacheDirectory()} is guaranteed to be empty.
     *
     * @throws ProvisioningException
     *     If the base server JAR file could not be created.
     */
    protected abstract void createBaseServerJar()
        throws ProvisioningException;

    /**
     * Creates and initializes the base server.
     * <p>
     * When this method is called, {@link #baseServerDirectory()} is guaranteed to be empty.
     *
     * @throws ProvisioningException
     *     If the base server directory could not be initialized.
     */
    protected abstract void createBaseServer()
        throws ProvisioningException;

    /**
     * Deletes transient lock/socket files created by failed startup attempts.
     *
     * @throws ProvisioningException
     *     If cleanup fails.
     */
    protected final void cleanTransientFilesForRetry()
        throws ProvisioningException
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
            throw new ProvisioningException(
                "Failed to delete transient retry file in '%s'."
                    .formatted(baseServerDirectory()),
                exception
            );
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to clean transient files in base server directory '%s' before retry."
                    .formatted(baseServerDirectory()),
                exception
            );
        }
    }

    /**
     * Downloads a file from the specified URL to the target file path.
     *
     * @param url
     *     The URL from which to download the file.
     * @param targetFile
     *     The path where the downloaded file should be saved.
     * @throws ProvisioningException
     *     If the file could not be downloaded.
     */
    protected void downloadFile(String url, Path targetFile)
        throws ProvisioningException
    {
        try
        {
            log().log(System.Logger.Level.INFO, "Downloading file from %s to %s".formatted(url, targetFile));
            FileUtils.copyURLToFile(URI.create(url).toURL(), targetFile.toFile(), 10_000, 10_000);
        }
        catch (IOException e)
        {
            throw new ProvisioningException("Failed to download file from %s to %s".formatted(url, targetFile), e);
        }
    }

    /**
     * Creates the target server.
     * <p>
     * This method copies the entire base server directory to the target server directory.
     *
     * @throws ProvisioningException
     *     if the base server directory could not be copied to the target server directory.
     */
    protected void createTargetServer()
        throws ProvisioningException
    {
        FileUtil.copyDirectoryRecursively(baseServerDirectory, targetServerDirectory);
        rewriteTargetServerPropertiesWithReservedPort();
    }

    /**
     * Rewrites target-server {@code server.properties} with a port reserved for this target server materialization.
     *
     * @throws ProvisioningException
     *     If reading or updating the target {@code server.properties} fails.
     */
    protected void rewriteTargetServerPropertiesWithReservedPort()
        throws ProvisioningException
    {
        final Path targetServerPropertiesFile = targetServerDirectory.resolve("server.properties");
        if (Files.notExists(targetServerPropertiesFile))
        {
            throw new ProvisioningException(
                "Target server properties file '%s' does not exist."
                    .formatted(targetServerPropertiesFile)
            );
        }

        final int reservedPort = reserveTargetServerPort();
        TargetServerPropertiesConfigurer.rewriteWithRuntimePort(log(), targetServerPropertiesFile, reservedPort);
    }

    /**
     * Reserves a target-server TCP port.
     *
     * @return A currently available local TCP port.
     *
     * @throws ProvisioningException
     *     If no local TCP port could be reserved.
     */
    protected int reserveTargetServerPort()
        throws ProvisioningException
    {
        return TargetServerPropertiesConfigurer.reserveAvailableTcpPort();
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

    /**
     * Resolves the path to a (versioned) directory.
     * <p>
     * If versioned directories are enabled, the path will be {@code directoryRoot/name()/serverVersion}.
     * <p>
     * If versioned directories are disabled, the path will just be {@code directoryRoot/name()}.
     *
     * @param serverVersion
     *     The version of the server for which to resolve the directory.
     * @param directoryRoot
     *     The root directory to use as base for the directory.
     * @param versionedDirectories
     *     Whether to use versioned directories.
     * @return The resolved path to the directory.
     */
    protected Path resolveVersionedDirectory(
        String serverVersion,
        Path directoryRoot,
        boolean versionedDirectories)
    {
        Path ret = directoryRoot.resolve(name());

        if (versionedDirectories)
            ret = ret.resolve(serverVersion);

        return ret.resolve(serverSpecification.cacheKey());
    }

    /**
     * Checks whether a file should be recreated based on the provided parameters.
     * <p>
     * For example, this can be used to determine if a server JAR file or base server directory should be recreated
     * based on its current state.
     *
     * @param forceRecreate
     *     {@code true} if the file should be recreated regardless of its current state,
     * @param expiryDays
     *     The number of days after which the file should be considered expired and recreated.
     * @param file
     *     The file to check for recreation.
     */
    protected boolean shouldBeRecreated(boolean forceRecreate, int expiryDays, Path file)
    {
        if (forceRecreate)
        {
            log.log(System.Logger.Level.INFO, "Forcing recreation of '" + file + "'.");
            return true;
        }

        if (Files.notExists(file))
        {
            log.log(System.Logger.Level.INFO, "File '" + file + "' does not exist. Going to create it.");
            return true;
        }

        if (FileUtil.getFileAgeInDays(file) >= expiryDays)
        {
            log.log(
                System.Logger.Level.INFO,
                "File '" + file + "' is older than " + expiryDays + " days. Going to recreate it."
            );
            return true;
        }

        return false;
    }

    private void installEmbeddedAgentJar()
        throws ProvisioningException
    {
        final Path pluginsDirectory = targetServerDirectory().resolve("plugins");
        FileUtil.createDirectories(pluginsDirectory, "plugins directory");
        final Path targetAgentJar = pluginsDirectory.resolve(LightkeeperEmbeddedAgent.FILE_NAME);

        try (InputStream embeddedAgentStream = LightkeeperEmbeddedAgent.openStream())
        {
            Files.copy(embeddedAgentStream, targetAgentJar, StandardCopyOption.REPLACE_EXISTING);
            if (Files.notExists(targetAgentJar) || !Files.isReadable(targetAgentJar))
            {
                throw new ProvisioningException(
                    "Embedded LightKeeper agent was extracted to '%s' but is not readable."
                        .formatted(targetAgentJar)
                );
            }

            final String expectedSha256 = serverSpecification().agentJarSha256();
            if (expectedSha256 == null || expectedSha256.isBlank())
                throw new ProvisioningException("Embedded LightKeeper agent SHA-256 metadata is missing.");

            final String actualSha256 = HashUtil.sha256(targetAgentJar);
            if (!expectedSha256.equalsIgnoreCase(actualSha256))
            {
                throw new ProvisioningException(
                    "Installed LightKeeper agent hash mismatch. Expected %s, got %s."
                        .formatted(expectedSha256, actualSha256)
                );
            }

            log.log(System.Logger.Level.INFO, "Installed LightKeeper agent JAR at '%s'.".formatted(targetAgentJar));
        }
        catch (IOException exception)
        {
            throw new ProvisioningException(
                "Failed to extract embedded LightKeeper agent jar to '%s'."
                    .formatted(targetAgentJar),
                exception
            );
        }
    }

    public final Path targetServerDirectoryPath()
    {
        return targetServerDirectory;
    }

    public final Path targetJarFilePath()
    {
        return targetJarFile;
    }
}
