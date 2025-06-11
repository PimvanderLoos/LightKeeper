package nl.pim16aap2.lightkeeper.maven.serverprovider;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;
import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents a provider for a specific type of server.
 * <p>
 * This class serves as a base for different server providers, such as Spigot or Paper. It contains common properties
 * and methods that can be shared among different server types.
 */
@Accessors(fluent = true)
@Getter(AccessLevel.PROTECTED)
@ToString
public abstract class ServerProvider
{
    private static final String EULA_FILE_NAME = "eula.txt";

    private final Log log;

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
     * Indicates whether the base server should be recreated.
     * <p>
     * This is determined based on the server specification and the current state of the base server directory.
     */
    private final boolean shouldRecreateBaseServer;

    /**
     * Indicates whether the server JAR file should be recreated.
     * <p>
     * This is determined based on the server specification and the current state of the JAR file in the cache.
     */
    private final boolean shouldRecreateJar;

    protected ServerProvider(Log log, String name, ServerSpecification serverSpecification)
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
        this.shouldRecreateJar = shouldBeRecreated(
            serverSpecification.forceRebuildJar(),
            serverSpecification.jarCacheExpiryDays(),
            this.jarCacheDirectory.resolve(outputJarFileName)
        );

        this.targetJarFile = this.targetServerDirectory.resolve(outputJarFileName);

        this.shouldRecreateBaseServer = shouldBeRecreated(
            serverSpecification.forceRecreateBaseServer(),
            serverSpecification.baseServerCacheExpiryDays(),
            this.baseServerDirectory.resolve(EULA_FILE_NAME)
        ) || !this.baseServerVersionMatches(
            this.baseServerDirectory,
            outputJarFileName
        );
    }

    /**
     * Checks whether the base server version matches the expected version.
     * <p>
     * If a base server was created for version {@code X} but the expected version is {@code Y} where {@code X != Y},
     * this method will return {@code false}, as the base server is not compatible with the expected version.
     *
     * @param baseServerDirectory
     *     The directory where the base server is cached.
     * @return {@code true} if the base server version matches the expected version,
     */
    protected boolean baseServerVersionMatches(Path baseServerDirectory, String outputJarFileName)
    {
        final Path expectedJarFile = baseServerDirectory.resolve(outputJarFileName);
        if (Files.notExists(expectedJarFile))
        {
            log.info("Expected JAR file '" + expectedJarFile + "' does not exist.");
            return false;
        }
        return true;
    }

    /**
     * Gets the file name of the output JAR file for this server type.
     * <p>
     * For example, for Spigot, this would be {@code spigot-1.20.1.jar}.
     *
     * @return The file name of the output JAR file.
     */
    protected String getOutputJarFileName()
    {
        return "%s-%s.jar".formatted(name(), serverSpecification().serverVersion());
    }

    /**
     * Prepares the server for use.
     */
    public final void prepareServer()
        throws MojoExecutionException
    {
        if (shouldRecreateJar())
        {
            log().info("Recreating server JAR file");
            FileUtil.cleanDirectory(jarCacheDirectory, "jar cache directory");
            createBaseServerJar();
        }

        if (shouldRecreateBaseServer())
        {
            log().info("Recreating base server directory");
            FileUtil.cleanDirectory(baseServerDirectory, "base server directory");
            createBaseServer();
        }

        log().info("Copying base server to target server directory");
        FileUtil.cleanDirectory(targetServerDirectory, "target server directory");
        createTargetServer();
    }

    /**
     * Creates the base server JAR file.
     * <p>
     * When this method is called, {@link #jarCacheDirectory()} is guaranteed to be empty.
     *
     * @throws MojoExecutionException
     *     If the base server JAR file could not be created.
     */
    protected abstract void createBaseServerJar()
        throws MojoExecutionException;

    /**
     * Creates and initializes the base server.
     * <p>
     * When this method is called, {@link #baseServerDirectory()} is guaranteed to be empty.
     *
     * @throws MojoExecutionException
     */
    protected abstract void createBaseServer()
        throws MojoExecutionException;

    /**
     * Downloads a file from the specified URL to the target file path.
     *
     * @param url
     *     The URL from which to download the file.
     * @param targetFile
     *     The path where the downloaded file should be saved.
     * @throws MojoExecutionException
     *     If the file could not be downloaded.
     */
    protected void downloadFile(String url, Path targetFile)
        throws MojoExecutionException
    {
        try
        {
            log().info("Downloading file from %s to %s".formatted(url, targetFile));
            FileUtils.copyURLToFile(URI.create(url).toURL(), targetFile.toFile(), 10_000, 10_000);
        }
        catch (IOException e)
        {
            throw new MojoExecutionException("Failed to download file from %s to %s".formatted(url, targetFile), e);
        }
    }

    /**
     * Creates the target server.
     * <p>
     * This method copies the entire base server directory to the target server directory.
     *
     * @throws MojoExecutionException
     *     if the base server directory could not be copied to the target server directory.
     */
    protected void createTargetServer()
        throws MojoExecutionException
    {
        try
        {
            Files.deleteIfExists(targetServerDirectory);
            Files.copy(baseServerDirectory, targetServerDirectory);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to copy base server directory %s to target server directory %s"
                    .formatted(baseServerDirectory, targetServerDirectory),
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
        final Path ret = directoryRoot.resolve(name());

        if (versionedDirectories)
            return ret.resolve(serverVersion);

        return ret;
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
            log.info("Forcing recreation of '" + file + "'.");
            return true;
        }

        if (Files.notExists(file))
        {
            log.info("File '" + file + "' does not exist. Going to create it.");
            return true;
        }

        if (FileUtil.getFileAgeInDays(file) >= expiryDays)
        {
            log.info("File '" + file + "' is older than " + expiryDays + " days. Going to recreate it.");
            return true;
        }

        return false;
    }
}
