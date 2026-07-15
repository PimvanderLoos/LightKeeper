package nl.pim16aap2.lightkeeper.framework;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * LIVE, composable entity query for one world: filters build new immutable queries, terminals re-query the
 * server per call.
 *
 * <p>{@link #count()} is a single cheap RPC, the natural probe for
 * {@code eventually(timeout, () -> assertThat(query.count()).isEqualTo(n))}; {@link #snapshot()} reads all
 * matching entities in one main-thread burst, so the returned states are internally consistent and share one
 * tick stamp — the property that makes "N in flight, zero after" accounting deterministic.
 *
 * <p>Only entities in loaded chunks are visible (the Bukkit contract). In a world with no players keep the
 * relevant chunks loaded — e.g. via the console command {@code forceload add} — or the server unloads them,
 * taking their entities out of query range.
 */
public final class EntityQuery
{
    private final IFrameworkGatewayView frameworkGateway;
    private final String worldName;
    private final @Nullable String entityTypeKey;
    private final @Nullable BlockPos boundsMin;
    private final @Nullable BlockPos boundsMax;

    /**
     * Creates an unfiltered query for a world.
     *
     * @param frameworkGateway
     *     Internal gateway for operations.
     * @param worldName
     *     Name of the world to query.
     */
    EntityQuery(IFrameworkGatewayView frameworkGateway, String worldName)
    {
        this(frameworkGateway, worldName, null, null, null);
    }

    private EntityQuery(
        IFrameworkGatewayView frameworkGateway,
        String worldName,
        @Nullable String entityTypeKey,
        @Nullable BlockPos boundsMin,
        @Nullable BlockPos boundsMax)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
        this.worldName = Objects.requireNonNull(worldName, "worldName may not be null.");
        this.entityTypeKey = entityTypeKey;
        this.boundsMin = boundsMin;
        this.boundsMax = boundsMax;
    }

    /**
     * Returns a copy of this query filtered to one entity type.
     *
     * @param typeKey
     *     Namespaced entity type key, e.g. {@code minecraft:block_display} (the {@code minecraft:} prefix is
     *     optional).
     * @return A new query with the type filter applied.
     */
    public EntityQuery ofType(String typeKey)
    {
        final String trimmed = Objects.requireNonNull(typeKey, "typeKey may not be null.").trim();
        if (trimmed.isEmpty())
            throw new IllegalArgumentException("typeKey may not be blank.");
        return new EntityQuery(frameworkGateway, worldName, trimmed, boundsMin, boundsMax);
    }

    /**
     * Returns a copy of this query restricted to a block-aligned bounding box (inclusive on every axis).
     *
     * @param min
     *     Minimum corner.
     * @param max
     *     Maximum corner.
     * @return A new query with the spatial bound applied.
     */
    public EntityQuery within(BlockPos min, BlockPos max)
    {
        Objects.requireNonNull(min, "min may not be null.");
        Objects.requireNonNull(max, "max may not be null.");
        if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z())
            throw new IllegalArgumentException("min must be <= max on every axis.");
        return new EntityQuery(frameworkGateway, worldName, entityTypeKey, min, max);
    }

    /**
     * Counts the matching entities — one RPC, the cheap probe shape for retrying assertions.
     *
     * @return The number of matching entities right now.
     */
    public int count()
    {
        return frameworkGateway.countEntities(worldName, entityTypeKey, boundsMin, boundsMax);
    }

    /**
     * Snapshots all matching entities in one main-thread burst.
     *
     * @return Internally consistent snapshots sharing one tick stamp.
     */
    public List<EntitySnapshot> snapshot()
    {
        return frameworkGateway.snapshotEntities(worldName, entityTypeKey, boundsMin, boundsMax);
    }
}
