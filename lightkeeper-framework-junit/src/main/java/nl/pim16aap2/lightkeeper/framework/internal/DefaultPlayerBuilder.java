package nl.pim16aap2.lightkeeper.framework.internal;

import nl.pim16aap2.lightkeeper.framework.IPlayerBuilder;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

final class DefaultPlayerBuilder implements IPlayerBuilder
{
    private final DefaultLightkeeperFramework framework;

    private UUID uuid = UUID.randomUUID();
    private String name = "lk_bot_" + uuid.toString().substring(0, 8);
    private @Nullable WorldHandle worldHandle;
    private @Nullable Double x;
    private @Nullable Double y;
    private @Nullable Double z;
    private @Nullable Double health;
    private final Set<String> permissions = new HashSet<>();

    DefaultPlayerBuilder(DefaultLightkeeperFramework framework)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
    }

    @Override
    public IPlayerBuilder withName(String name)
    {
        this.name = DefaultLightkeeperFramework.validatePlayerName(name);
        return this;
    }

    @Override
    public IPlayerBuilder withRandomName()
    {
        this.uuid = UUID.randomUUID();
        this.name = "lk_bot_" + uuid.toString().substring(0, 8);
        return this;
    }

    @Override
    public IPlayerBuilder inWorld(WorldHandle world)
    {
        this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
        this.x = null;
        this.y = null;
        this.z = null;
        return this;
    }

    @Override
    public IPlayerBuilder atLocation(WorldHandle world, double x, double y, double z)
    {
        this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    @Override
    public IPlayerBuilder atSpawn(WorldHandle world)
    {
        this.worldHandle = Objects.requireNonNull(world, "world may not be null.");
        this.x = null;
        this.y = null;
        this.z = null;
        return this;
    }

    @Override
    public IPlayerBuilder withHealth(double health)
    {
        if (health <= 0.0D)
            throw new IllegalArgumentException("health must be > 0.");
        this.health = health;
        return this;
    }

    @Override
    public IPlayerBuilder withPermissions(String... permissions)
    {
        if (permissions == null || permissions.length == 0)
            return this;
        Arrays.stream(permissions)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(permission -> !permission.isEmpty())
            .forEach(this.permissions::add);
        return this;
    }

    @Override
    public PlayerHandle build()
    {
        framework.ensureOpen();
        final WorldHandle effectiveWorldHandle = Objects.requireNonNull(
            worldHandle,
            "world must be configured via inWorld/atLocation/atSpawn."
        );

        return framework.createPlayerFromBuilder(
            DefaultLightkeeperFramework.validatePlayerName(name),
            uuid,
            effectiveWorldHandle,
            x,
            y,
            z,
            health,
            permissions
        );
    }
}
