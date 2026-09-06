package nl.pim16aap2.lightkeeper.maven.mojo.prepareserver;

import nl.pim16aap2.lightkeeper.maven.provisioning.ResolvedPluginArtifact;
import nl.pim16aap2.lightkeeper.maven.provisioning.WorldInputSpec;
import nl.pim16aap2.lightkeeper.maven.util.HashUtil;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeArtifactHasher;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscovery;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscoveryReader;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeDiscoveryWriter;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimePaths;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeProvenance;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeProvenanceWriter;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeValidator;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestReader;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifestValidator;
import nl.pim16aap2.lightkeeper.runtime.RuntimeProtocol;
import org.apache.maven.plugin.MojoExecutionException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Computes durable IDE preparation identity and atomically publishes module-owned discovery metadata.
 */
final class IdeRuntimePreparationSupport
{
    private static final String GENERATED_IGNORE_CONTENT = "*\n";

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
            final RuntimeManifest manifest = new RuntimeManifestReader().read(
                Path.of(discovery.runtimeManifestPath())
            );
            RuntimeManifestValidator.validateForRuntimeStartup(manifest, RuntimeProtocol.VERSION);
            IdeRuntimeValidator.validate(discovery, moduleDirectory, manifest);
            return discovery;
        }
        catch (IOException | RuntimeException exception)
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

    static void writeProvenance(
        Path moduleDirectory,
        String executionId,
        String fingerprint,
        Path manifestPath,
        RuntimeManifest manifest,
        List<ResolvedPluginArtifact> plugins,
        List<WorldInputSpec> worlds,
        @Nullable Path configOverlayPath)
        throws MojoExecutionException
    {
        final List<IdeRuntimeProvenance.Artifact> requiredArtifacts = new ArrayList<>();
        requiredArtifacts.add(artifact(Path.of(manifest.serverJar())));
        requiredArtifacts.add(artifact(Path.of(Objects.requireNonNull(
            manifest.agentJar(),
            "IDE runtime manifest agentJar may not be null."
        ))));
        final Path pluginsDirectory = Path.of(manifest.serverDirectory()).resolve("plugins");
        plugins.stream()
            .sorted(Comparator.comparing(ResolvedPluginArtifact::outputFileName))
            .map(plugin -> artifact(pluginsDirectory.resolve(plugin.outputFileName())))
            .forEach(requiredArtifacts::add);

        final List<IdeRuntimeProvenance.Artifact> sourceInputs = new ArrayList<>();
        plugins.stream()
            .sorted(Comparator.comparing(plugin -> canonical(plugin.sourceJar())))
            .map(plugin -> artifact(plugin.sourceJar()))
            .forEach(sourceInputs::add);
        worlds.stream()
            .sorted(Comparator.comparing(world -> canonical(world.sourcePath())))
            .map(world -> artifact(world.sourcePath()))
            .forEach(sourceInputs::add);
        if (configOverlayPath != null)
            sourceInputs.add(artifact(configOverlayPath));

        final IdeRuntimeProvenance provenance = new IdeRuntimeProvenance(
            IdeRuntimeProvenance.SCHEMA_VERSION,
            canonical(moduleDirectory),
            executionId,
            manifest.serverType(),
            fingerprint,
            manifest.runtimeProtocolVersion(),
            requiredArtifacts,
            sourceInputs
        );
        try
        {
            new IdeRuntimeProvenanceWriter().write(
                provenance,
                manifestPath.resolveSibling(IdeRuntimePaths.PROVENANCE_FILE_NAME)
            );
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException("Failed to write IDE runtime provenance.", exception);
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
            return IdeRuntimeArtifactHasher.sha256(input);
        }
        catch (IOException exception)
        {
            throw new IllegalStateException(
                "Failed to fingerprint preparation input '%s'.".formatted(input),
                exception
            );
        }
    }

    private static IdeRuntimeProvenance.Artifact artifact(Path path)
    {
        final Path absolutePath = path.toAbsolutePath().normalize();
        try
        {
            return new IdeRuntimeProvenance.Artifact(
                absolutePath.toString(),
                IdeRuntimeArtifactHasher.sha256(absolutePath)
            );
        }
        catch (IOException exception)
        {
            throw new IllegalStateException("Failed to hash IDE runtime artifact '%s'.".formatted(path), exception);
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
