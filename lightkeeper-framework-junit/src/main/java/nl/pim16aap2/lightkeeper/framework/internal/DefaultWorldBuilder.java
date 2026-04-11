package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.IWorldBuilder;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.framework.WorldSpec;

import java.util.Objects;

final class DefaultWorldBuilder implements IWorldBuilder
{
    private final DefaultLightkeeperFramework framework;

    private String name = DefaultLightkeeperFramework.createDefaultWorldName();
    private WorldSpec.WorldType worldType = DefaultLightkeeperFramework.DEFAULT_WORLD_TYPE;
    private WorldSpec.WorldEnvironment environment = DefaultLightkeeperFramework.DEFAULT_WORLD_ENVIRONMENT;
    private long seed = DefaultLightkeeperFramework.DEFAULT_WORLD_SEED;

    DefaultWorldBuilder(DefaultLightkeeperFramework framework)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
    }

    @Override
    public IWorldBuilder withName(String name)
    {
        this.name = Objects.requireNonNull(name, "name may not be null.");
        return this;
    }

    @Override
    public IWorldBuilder withRandomName()
    {
        this.name = DefaultLightkeeperFramework.createDefaultWorldName();
        return this;
    }

    @Override
    public IWorldBuilder withWorldType(WorldSpec.WorldType worldType)
    {
        this.worldType = Objects.requireNonNull(worldType, "worldType may not be null.");
        return this;
    }

    @Override
    public IWorldBuilder withEnvironment(WorldSpec.WorldEnvironment environment)
    {
        this.environment = Objects.requireNonNull(environment, "environment may not be null.");
        return this;
    }

    @Override
    public IWorldBuilder withSeed(long seed)
    {
        this.seed = seed;
        return this;
    }

    @Override
    public WorldHandle build()
    {
        framework.ensureOpen();
        return framework.createWorldFromBuilder(new WorldSpec(name, worldType, environment, seed));
    }
}
