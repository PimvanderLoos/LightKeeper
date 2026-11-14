package nl.pim16aap2.lightkeeper.maven.mojo.lockbuild;

import nl.pim16aap2.lightkeeper.provisioning.util.FileUtil;
import nl.pim16aap2.lightkeeper.runtime.GeneratedMetadata;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimeArtifactHasher;
import nl.pim16aap2.lightkeeper.runtime.IdeRuntimePaths;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Invalidates generated production outputs when their effective inputs change, then lets Maven rebuild normally.
 * Maven resources otherwise retain deleted resource files. This never compiles sources, selects a target artifact,
 * skips packaging, or removes test classes; unchanged inputs keep their existing incremental outputs.
 */
final class ProductionOutputInvalidator
{
    private ProductionOutputInvalidator()
    {
    }

    static void refresh(MavenProject project, Properties userProperties, Log log)
        throws MojoExecutionException
    {
        if ("pom".equals(project.getPackaging()))
        {
            return;
        }
        try
        {
            final Path module = project.getBasedir().toPath().toRealPath();
            IdeRuntimePaths.prepareStateDirectory(module);
            final Path build = canonicalCandidate(Path.of(project.getBuild().getDirectory()));
            final Path output = canonicalCandidate(Path.of(project.getBuild().getOutputDirectory()));
            final Path testOutput = canonicalCandidate(Path.of(project.getBuild().getTestOutputDirectory()));
            final Path testSources = canonicalCandidate(Path.of(project.getBuild().getTestSourceDirectory()));
            final List<Path> inputs = inputDirectories(project, build);
            final List<Path> outputs = List.of(output, canonicalCandidate(build.resolve("generated-sources")));
            for (final Path target : outputs)
            {
                validateOutput(target, module, testOutput, testSources, inputs);
            }
            final String fingerprint = fingerprint(project, userProperties, inputs, output);
            final Path stateFile = IdeRuntimePaths.stateDirectory(module).resolve("build-inputs/production.json");
            if (Files.isRegularFile(stateFile, LinkOption.NOFOLLOW_LINKS))
            {
                final OutputState previous = GeneratedMetadata.read(stateFile, OutputState.class);
                if (previous.fingerprint().equals(fingerprint) && previous.outputDirectory().equals(output.toString()))
                {
                    return;
                }
            }
            log.info("LK_BUILD_INPUTS: Refreshing generated production outputs for '" + project.getArtifactId() + "'.");
            for (final Path target : outputs)
            {
                FileUtil.cleanDirectory(target, "generated production output");
            }
            GeneratedMetadata.write(new OutputState(fingerprint, output.toString()), stateFile);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Cannot refresh generated production outputs for '" + project.getArtifactId() + "'.",
                exception
            );
        }
    }

    private static List<Path> inputDirectories(MavenProject project, Path build)
        throws IOException
    {
        final TreeSet<String> candidates = new TreeSet<>(project.getCompileSourceRoots());
        project.getResources().forEach(resource -> candidates.add(resource.getDirectory()));
        final List<Path> inputs = new ArrayList<>();
        for (final String candidate : candidates)
        {
            final Path path = canonicalCandidate(Path.of(candidate));
            if (!path.startsWith(build))
            {
                inputs.add(path);
            }
        }
        return List.copyOf(inputs);
    }

    private static String fingerprint(MavenProject project, Properties userProperties, List<Path> inputs, Path output)
        throws IOException
    {
        final StringWriter model = new StringWriter();
        new MavenXpp3Writer().write(model, project.getModel());
        final StringBuilder values = new StringBuilder(model.toString()).append('\n').append(output);
        final TreeMap<String, String> properties = new TreeMap<>();
        userProperties.stringPropertyNames().stream()
            .filter(name -> !isInvocationControl(name))
            .forEach(name -> properties.put(name, userProperties.getProperty(name)));
        // Only the final digest is persisted. Effective model/property values can contain private build settings.
        properties.forEach((name, value) -> values.append('\n').append(name).append('=').append(value));
        for (final Path input : inputs)
        {
            values.append('\n')
                .append(input)
                .append(':')
                .append(Files.exists(input) ? IdeRuntimeArtifactHasher.sha256(input) : "missing");
        }
        return IdeRuntimeArtifactHasher.sha256(values.toString());
    }

    private static boolean isInvocationControl(String name)
    {
        return name.equals("skipTests") || name.equals("skipITs") || name.equals("maven.test.skip") ||
            name.equals("test") || name.equals("it.test") || name.equals("lightkeeper.skip") ||
            name.equals("lightkeeper.ide") || name.startsWith("lightkeeper.ide.") ||
            name.startsWith("lightkeeper.bootstrap.");
    }

    private static void validateOutput(
        Path output,
        Path module,
        Path testOutput,
        Path testSources,
        List<Path> inputs)
    {
        if (module.startsWith(output) || output.startsWith(module.resolve(".git")) ||
            output.startsWith(IdeRuntimePaths.stateDirectory(module)) ||
            overlaps(output, testOutput) || overlaps(output, testSources) ||
            inputs.stream().anyMatch(input -> overlaps(output, input)))
        {
            throw new IllegalArgumentException(
                "Production output '%s' overlaps source, test, project, or LK state files; configure separate outputs."
                    .formatted(output)
            );
        }
    }

    private static boolean overlaps(Path first, Path second)
    {
        return first.startsWith(second) || second.startsWith(first);
    }

    private static Path canonicalCandidate(Path path)
        throws IOException
    {
        Path existing = path.toAbsolutePath().normalize();
        final ArrayDeque<String> missing = new ArrayDeque<>();
        while (!Files.exists(existing, LinkOption.NOFOLLOW_LINKS))
        {
            missing.addFirst(Objects.requireNonNull(existing.getFileName()).toString());
            existing = Objects.requireNonNull(existing.getParent());
        }
        Path result = existing.toRealPath();
        for (final String component : missing)
        {
            result = result.resolve(component);
        }
        return result;
    }

    /**
     * Last input identity for which production outputs were invalidated; never evidence of a successful build.
     *
     * @param fingerprint
     *     Digest of effective production inputs, excluding invocation/test control flags.
     * @param outputDirectory
     *     Canonical production output location that was cleared.
     */
    private record OutputState(String fingerprint, String outputDirectory)
    {
        private OutputState
        {
            if (!Objects.requireNonNull(fingerprint).matches("[a-f0-9]{64}") ||
                !Path.of(outputDirectory).isAbsolute())
            {
                throw new IllegalArgumentException("Invalid generated production input identity.");
            }
        }
    }
}
