package nl.pim16aap2.lightkeeper.maven.provisioning;

import java.nio.file.Path;

/**
 * Validated world provisioning input.
 *
 * @param name
 *     Target world directory name.
 * @param sourceType
 *     Source type.
 * @param sourcePath
 *     Source path.
 * @param overwrite
 *     Whether to overwrite an existing world directory.
 * @param loadOnStartup
 *     Whether to preload this world in the framework startup sequence.
 * @param environment
 *     Environment enum name.
 * @param worldType
 *     World type enum name.
 * @param seed
 *     World seed.
 */
public record WorldInputSpec(
    String name,
    SourceType sourceType,
    Path sourcePath,
    boolean overwrite,
    boolean loadOnStartup,
    String environment,
    String worldType,
    long seed
)
{
    /**
     * Source type for world import.
     */
    public enum SourceType
    {
        FOLDER,
        ARCHIVE
    }
}
