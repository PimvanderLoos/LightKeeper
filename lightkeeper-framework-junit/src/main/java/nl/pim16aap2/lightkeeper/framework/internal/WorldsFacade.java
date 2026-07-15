package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.FrameworkHandleFactory;
import nl.pim16aap2.lightkeeper.framework.IWorldBuilder;
import nl.pim16aap2.lightkeeper.framework.IWorlds;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;

import java.util.Objects;

/**
 * Default {@link IWorlds} implementation.
 *
 * <p>Wraps the shared framework internals handed to it by {@link DefaultLightkeeperFramework}: it validates world
 * specs and delegates world creation to the agent client, deferring the shared open-state gate to the owning
 * framework.
 */
final class WorldsFacade implements IWorlds
{
    private static final System.Logger LOG = System.getLogger(WorldsFacade.class.getName());

    private final DefaultLightkeeperFramework framework;
    private final RuntimeManifest runtimeManifest;
    private final UdsAgentClient agentClient;

    WorldsFacade(
        DefaultLightkeeperFramework framework,
        RuntimeManifest runtimeManifest,
        UdsAgentClient agentClient)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
        this.runtimeManifest = Objects.requireNonNull(runtimeManifest, "runtimeManifest may not be null.");
        this.agentClient = Objects.requireNonNull(agentClient, "agentClient may not be null.");
    }

    @Override
    public WorldHandle main()
    {
        framework.ensureOpen();
        return FrameworkHandleFactory.worldHandle(framework, agentClient.mainWorld());
    }

    @Override
    public WorldHandle create()
    {
        return create(defaultWorldSpec());
    }

    @Override
    public WorldHandle create(WorldSpec worldSpec)
    {
        framework.ensureOpen();
        final WorldSpec validatedWorldSpec = validateWorldSpec(worldSpec);
        final String worldName = agentClient.newWorld(validatedWorldSpec);
        LOG.log(
            System.Logger.Level.INFO,
            () -> "LK_FRAMEWORK: Created world '" + worldName + "'."
        );
        return FrameworkHandleFactory.worldHandle(framework, worldName);
    }

    @Override
    public WorldHandle fromTemplate(String templateName)
    {
        framework.ensureOpen();
        final String trimmedTemplateName =
            Objects.requireNonNull(templateName, "templateName may not be null.").trim();
        if (trimmedTemplateName.isEmpty())
            throw new IllegalArgumentException("templateName may not be blank.");
        final RuntimeManifest.ProvisionedWorld template = runtimeManifest.provisionedWorlds().stream()
            .filter(provisionedWorld -> provisionedWorld.name().equals(trimmedTemplateName))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException(
                "No world template named '%s' was provisioned. Provisioned templates: %s".formatted(
                    trimmedTemplateName,
                    runtimeManifest.provisionedWorlds().stream()
                        .map(RuntimeManifest.ProvisionedWorld::name)
                        .toList())));

        // Pass the template's own provisioned spec: folders that lack complete world data are generated with
        // the configured settings, while folders with real world data load from their own files and ignore it.
        final String worldName = agentClient.newWorld(DefaultLightkeeperFramework.toWorldSpec(template));
        LOG.log(
            System.Logger.Level.INFO,
            () -> "LK_FRAMEWORK: Loaded world '" + worldName + "' from a provisioned template."
        );
        return FrameworkHandleFactory.worldHandle(framework, worldName);
    }

    @Override
    public IWorldBuilder builder()
    {
        framework.ensureOpen();
        return new DefaultWorldBuilder(framework);
    }

    private static WorldSpec validateWorldSpec(WorldSpec worldSpec)
    {
        Objects.requireNonNull(worldSpec, "worldSpec may not be null.");
        final String worldName = Objects.requireNonNull(worldSpec.name(), "worldSpec.name may not be null.").trim();
        if (worldName.isEmpty())
            throw new IllegalArgumentException("worldSpec.name may not be blank.");

        final WorldSpec.WorldType worldType =
            Objects.requireNonNull(worldSpec.worldType(), "worldSpec.worldType may not be null.");
        final WorldSpec.WorldEnvironment worldEnvironment =
            Objects.requireNonNull(worldSpec.environment(), "worldSpec.environment may not be null.");
        return new WorldSpec(worldName, worldType, worldEnvironment, worldSpec.seed());
    }

    private static WorldSpec defaultWorldSpec()
    {
        return new WorldSpec(
            DefaultLightkeeperFramework.createDefaultWorldName(),
            DefaultLightkeeperFramework.DEFAULT_WORLD_TYPE,
            DefaultLightkeeperFramework.DEFAULT_WORLD_ENVIRONMENT,
            DefaultLightkeeperFramework.DEFAULT_WORLD_SEED
        );
    }
}
