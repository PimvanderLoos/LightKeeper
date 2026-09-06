package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscovery;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscoveryReader;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscoveryWriter;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimePaths;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Computes durable IDE preparation identity and atomically publishes module-owned discovery metadata.
 */
final class IdeRuntimePreparationSupport
{
    private static final String GENERATED_IGNORE_CONTENT = "*\n!.gitignore\n";

    private IdeRuntimePreparationSupport()
    {
    }

    static String fingerprint(
        Path moduleDirectory,
        String executionId,
        PrepareServerExecutionContext context,
        PrepareServerRuntimePreparation preparation,
        List<ResolvedPluginArtifact> plugins,
        @Nullable Path configOverlayPath,
        int memoryMb,
        @Nullable String extraJvmArgs,
        @Nullable String javaExecutablePath)
        throws MojoExecutionException
    {
        final List<String> inputs = new ArrayList<>();
        inputs.add("schema=" + IdeRuntimeDiscovery.SCHEMA_VERSION);
        inputs.add("module=" + canonical(moduleDirectory));
        inputs.add("execution=" + executionId);
        inputs.add("serverType=" + context.normalizedServerType());
        inputs.add("configuredServerVersion=" + context.serverVersion());
        inputs.add("resolvedServerVersion=" + preparation.resolvedServerSetup().manifestServerVersion());
        inputs.add("buildId=" + preparation.resolvedServerSetup().manifestBuildId());
        inputs.add("serverCacheKey=" + preparation.resolvedServerSetup().cacheKey());
        inputs.add("agent=" + preparation.agentMetadata().sha256());
        inputs.add("protocol=" + preparation.runtimeProtocolVersion());
        inputs.add("memoryMb=" + memoryMb);
        inputs.add("java=" + javaExecutablePath);
        inputs.add("extraJvmArgs=" + extraJvmArgs);

        plugins.stream()
            .sorted(Comparator.comparing(ResolvedPluginArtifact::outputFileName)
                .thenComparing(ResolvedPluginArtifact::sourceDescription))
            .forEach(plugin -> inputs.add(pluginFingerprint(plugin)));
        context.worldInputSpecs().stream()
            .sorted(Comparator.comparing(WorldInputSpec::name))
            .forEach(world -> inputs.add(worldFingerprint(world)));
        if (configOverlayPath != null)
            inputs.add("overlay=" + canonical(configOverlayPath) + ":" + hashInput(configOverlayPath));

        return HashUtil.sha256(String.join("\n", inputs));
    }

    static @Nullable IdeRuntimeDiscovery reusableDiscovery(
        Path moduleDirectory,
        String executionId,
        String serverType,
        String fingerprint)
    {
        final Path discoveryPath = IdeRuntimePaths.discoveryFile(moduleDirectory);
        if (!Files.isRegularFile(discoveryPath))
            return null;
        try
        {
            final IdeRuntimeDiscovery discovery = new IdeRuntimeDiscoveryReader().read(discoveryPath);
            if (!discovery.moduleDirectory().equals(canonical(moduleDirectory)) ||
                !discovery.executionId().equals(executionId) ||
                !discovery.serverType().equals(serverType) ||
                !discovery.preparationFingerprint().equals(fingerprint) ||
                !Files.isRegularFile(Path.of(discovery.runtimeManifestPath())))
                return null;
            return discovery;
        }
        catch (IOException | IllegalArgumentException exception)
        {
            return null;
        }
    }

    static Path newPreparationDirectory(Path moduleDirectory, String fingerprint, boolean refresh)
    {
        final String directoryName = refresh
            ? fingerprint + "-" + UUID.randomUUID().toString().substring(0, 8)
            : fingerprint;
        return IdeRuntimePaths.preparationsDirectory(moduleDirectory).resolve(directoryName);
    }

    static void publish(
        Path moduleDirectory,
        String executionId,
        String serverType,
        String fingerprint,
        Path manifestPath)
        throws MojoExecutionException
    {
        try
        {
            createGeneratedIgnoreFile(moduleDirectory);
            new IdeRuntimeDiscoveryWriter().write(
                new IdeRuntimeDiscovery(
                    IdeRuntimeDiscovery.SCHEMA_VERSION,
                    canonical(moduleDirectory),
                    executionId,
                    serverType,
                    manifestPath.toAbsolutePath().normalize().toString(),
                    fingerprint
                ),
                IdeRuntimePaths.discoveryFile(moduleDirectory)
            );
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to publish IDE test setup for module '%s'.".formatted(moduleDirectory),
                exception
            );
        }
    }

    private static String pluginFingerprint(ResolvedPluginArtifact plugin)
    {
        return "plugin=" + plugin.outputFileName() + ":" + plugin.sourceDescription() + ":" +
            canonical(plugin.sourceJar()) + ":" + hashInput(plugin.sourceJar());
    }

    private static String worldFingerprint(WorldInputSpec world)
    {
        return "world=" + world.name() + ":" + world.sourceType() + ":" + world.overwrite() + ":" +
            world.loadOnStartup() + ":" + world.environment() + ":" + world.worldType() + ":" + world.seed() +
            ":" + canonical(world.sourcePath()) + ":" + hashInput(world.sourcePath());
    }

    private static String hashInput(Path input)
    {
        try
        {
            if (Files.isRegularFile(input))
                return HashUtil.sha256(input);
            if (!Files.isDirectory(input))
                throw new MojoExecutionException("Preparation input '%s' does not exist.".formatted(input));

            final List<String> entries;
            try (Stream<Path> stream = Files.walk(input))
            {
                entries = stream
                    .filter(path -> !path.equals(input))
                    .sorted(Comparator.comparing(path -> input.relativize(path).toString()))
                    .map(path -> hashDirectoryEntry(input, path))
                    .toList();
            }
            return HashUtil.sha256(String.join("\n", entries));
        }
        catch (IOException | MojoExecutionException exception)
        {
            throw new IllegalStateException(
                "Failed to fingerprint preparation input '%s'.".formatted(input),
                exception
            );
        }
    }

    private static String hashDirectoryEntry(Path root, Path path)
    {
        if (Files.isSymbolicLink(path))
            throw new IllegalStateException("Symbolic links are not allowed in IDE preparation inputs: " + path);
        final String relative = root.relativize(path).toString().replace(path.getFileSystem().getSeparator(), "/");
        if (Files.isDirectory(path))
            return "directory:" + relative;
        try
        {
            return "file:" + relative + ":" + HashUtil.sha256(path);
        }
        catch (MojoExecutionException exception)
        {
            throw new IllegalStateException("Failed to fingerprint preparation input '%s'.".formatted(path), exception);
        }
    }

    private static String canonical(Path path)
    {
        try
        {
            return path.toRealPath().toString();
        }
        catch (IOException exception)
        {
            return path.toAbsolutePath().normalize().toString();
        }
    }

    private static void createGeneratedIgnoreFile(Path moduleDirectory)
        throws IOException
    {
        final Path stateDirectory = IdeRuntimePaths.stateDirectory(moduleDirectory);
        Files.createDirectories(stateDirectory);
        final Path ignoreFile = stateDirectory.resolve(".gitignore");
        if (Files.notExists(ignoreFile))
        {
            Files.writeString(
                ignoreFile,
                GENERATED_IGNORE_CONTENT,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE
            );
        }
    }
}
