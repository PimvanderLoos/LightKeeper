package nl.pim16aap2.lightkeeper.protocol;

import java.util.List;

/**
 * Represents a snapshot of a plugin on the server.
 *
 * @param name
 *     The name of the plugin as defined in its plugin.yml file.
 * @param version
 *     The version of the plugin as defined in its plugin.yml file.
 * @param description
 *     A brief description of the plugin as defined in its plugin.yml file.
 * @param authors
 *     The authors of the plugin as defined in its plugin.yml file.
 * @param isEnabled
 *     Whether the plugin is enabled.
 */
public record ServerPluginSnapshot(
    String name,
    String version,
    String description,
    List<String> authors,
    boolean isEnabled
)
{
    public ServerPluginSnapshot
    {
        authors = authors == null ? List.of() : List.copyOf(authors);
    }
}
