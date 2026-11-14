package nl.pim16aap2.lightkeeper.provisioning.assets;

import nl.pim16aap2.lightkeeper.provisioning.ProvisioningException;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.CRC32;

/**
 * Validates packaged plugin metadata without loading classes or requiring successful plugin startup.
 * YAML uses a safe constructor with bounded input size, aliases and nesting.
 */
public final class PluginDescriptorValidator
{
    /** Maximum uncompressed size of one plugin descriptor in bytes. */
    private static final int MAX_DESCRIPTOR_BYTES = 256 * 1024;
    /** Bounded archive traversal prevents corrupt or highly expanded payloads from exhausting preparation. */
    private static final long MAX_ENTRY_BYTES = 256L * 1024 * 1024;
    /** Maximum total uncompressed target-plugin payload checked before server provisioning. */
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024;
    /** Bound on central-directory entries visited during static archive verification. */
    private static final int MAX_ENTRIES = 100_000;

    private PluginDescriptorValidator()
    {
    }

    /**
     * Validates every present Bukkit/Paper descriptor and requires at least one descriptor.
     *
     * @param plugin
     *     Selected packaged plugin artifact.
     * @param module
     *     Owning test module identity for actionable diagnostics.
     * @throws ProvisioningException
     *     If the archive or its static descriptor metadata is invalid.
     */
    public static void validate(ResolvedPluginArtifact plugin, String module)
        throws ProvisioningException
    {
        try (JarFile jar = new JarFile(plugin.sourceJar().toFile(), false))
        {
            boolean found = false;
            for (final String name : List.of("plugin.yml", "paper-plugin.yml"))
            {
                final JarEntry entry = jar.getJarEntry(name);
                if (entry == null)
                {
                    continue;
                }
                found = true;
                try (var input = jar.getInputStream(entry))
                {
                    final byte[] bytes = input.readNBytes(MAX_DESCRIPTOR_BYTES + 1);
                    if (entry.isDirectory() || bytes.length > MAX_DESCRIPTOR_BYTES)
                    {
                        throw new IllegalArgumentException(
                            name + " must be a file of at most " + MAX_DESCRIPTOR_BYTES + " bytes"
                        );
                    }
                    validateDescriptor(new String(bytes, StandardCharsets.UTF_8), name);
                }
            }
            if (!found)
            {
                throw new IllegalArgumentException("the JAR contains neither plugin.yml nor paper-plugin.yml");
            }
            validateArchive(jar);
        }
        catch (IOException | IllegalArgumentException | YAMLException exception)
        {
            throw new ProvisioningException(
                "Invalid plugin under test for module '%s': '%s' (%s): %s. Select a packaged plugin with "
                    .formatted(module, plugin.sourceJar(), plugin.sourceDescription(), exception.getMessage())
                    + "pluginUnderTest.artifactId/classifier/path, or use serverOnly=true for server-only tests.",
                exception
            );
        }
    }

    private static void validateArchive(JarFile jar)
        throws IOException
    {
        final byte[] buffer = new byte[8192];
        final var entries = jar.entries();
        int count = 0;
        long total = 0;
        while (entries.hasMoreElements())
        {
            final JarEntry entry = entries.nextElement();
            if (++count > MAX_ENTRIES || entry.getSize() > MAX_ENTRY_BYTES)
            {
                throw new IllegalArgumentException("JAR exceeds the static archive validation limits");
            }
            final CRC32 checksum = new CRC32();
            long size = 0;
            try (var input = jar.getInputStream(entry))
            {
                int read;
                while ((read = input.read(buffer)) != -1)
                {
                    size += read;
                    total += read;
                    if (size > MAX_ENTRY_BYTES || total > MAX_ARCHIVE_BYTES)
                    {
                        throw new IllegalArgumentException("JAR exceeds the static archive validation limits");
                    }
                    checksum.update(buffer, 0, read);
                }
            }
            if (size != entry.getSize() || checksum.getValue() != entry.getCrc())
            {
                throw new IllegalArgumentException("corrupt JAR entry '" + entry.getName() + "': size/CRC mismatch");
            }
        }
    }

    private static void validateDescriptor(String contents, String descriptor)
    {
        final LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(25);
        options.setNestingDepthLimit(30);
        options.setCodePointLimit(MAX_DESCRIPTOR_BYTES);
        final Object document = new Yaml(new SafeConstructor(options)).load(contents);
        if (!(document instanceof Map<?, ?> fields))
        {
            throw new IllegalArgumentException(descriptor + " must contain a YAML mapping");
        }
        final String name = requiredScalar(fields, "name", descriptor);
        if (!name.matches("[A-Za-z0-9 _.-]+"))
        {
            throw new IllegalArgumentException(descriptor + ": name contains unsupported characters");
        }
        requiredScalar(fields, "version", descriptor);
        requiredScalar(fields, "main", descriptor);
        if (descriptor.equals("paper-plugin.yml"))
        {
            requiredScalar(fields, "api-version", descriptor);
        }
        final Object load = fields.get("load");
        if (load != null && !List.of("STARTUP", "POSTWORLD").contains(load.toString()))
        {
            throw new IllegalArgumentException(descriptor + ": load must be STARTUP or POSTWORLD");
        }
        for (final String key : List.of("depend", "softdepend", "loadbefore", "provides"))
        {
            final Object dependencies = fields.get(key);
            if (dependencies != null && !(dependencies instanceof List<?>))
            {
                throw new IllegalArgumentException(descriptor + ": " + key + " must be a list");
            }
        }
    }

    private static String requiredScalar(Map<?, ?> fields, String key, String descriptor)
    {
        final Object value = fields.get(key);
        if (!(value instanceof String || value instanceof Number) || value.toString().isBlank())
        {
            throw new IllegalArgumentException(descriptor + ": missing or invalid required field '" + key + "'");
        }
        return value.toString();
    }
}
