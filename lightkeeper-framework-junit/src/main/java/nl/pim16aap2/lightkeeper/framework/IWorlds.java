package nl.pim16aap2.lightkeeper.framework;

/**
 * Worlds facet of the framework: create worlds from defaults, specs, or provisioned templates, or configure one
 * through a builder.
 *
 * <p>Obtained from {@link ILightkeeperFramework#worlds()}.
 */
public interface IWorlds
{
    /**
     * Gets the main world handle.
     *
     * @return The main world handle.
     */
    WorldHandle main();

    /**
     * Creates a new world using framework defaults.
     *
     * @return The created world handle.
     */
    WorldHandle create();

    /**
     * Creates a new world from a world spec.
     *
     * @param worldSpec
     *     The world specification.
     * @return The created world handle.
     */
    WorldHandle create(WorldSpec worldSpec);

    /**
     * Loads a world from a pre-provisioned template folder.
     *
     * <p>Templates are world folders provisioned into the server directory by the Maven plugin's
     * {@code <worlds>} configuration (typically with {@code loadOnStartup=false}). The name is validated against
     * the runtime manifest's provisioned-template list <em>before</em> touching the server: a typo fails loudly
     * instead of silently creating a fresh world.
     *
     * <p>A provisioned world that is already loaded (e.g. one with {@code loadOnStartup=true}) is returned as-is
     * rather than reloaded.
     *
     * @param templateName
     *     The provisioned world folder's name.
     * @return A handle for the loaded world.
     * @throws IllegalArgumentException
     *     If no template with that name was provisioned; the message lists the available templates.
     */
    WorldHandle fromTemplate(String templateName);

    /**
     * Creates a world builder.
     *
     * @return A new world builder.
     */
    IWorldBuilder builder();
}
