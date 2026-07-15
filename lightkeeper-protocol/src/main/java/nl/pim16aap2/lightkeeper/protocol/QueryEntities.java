package nl.pim16aap2.lightkeeper.protocol;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Queries entities in a world, optionally filtered by type and spatial bounds.
 */
public final class QueryEntities
{
    private QueryEntities()
    {
    }

    /**
     * Command record for {@code QUERY_ENTITIES}.
     *
     * @param requestId
     *     Correlation identifier matching the response's {@code requestId}.
     * @param worldName
     *     Name of the world to query.
     * @param entityTypeKey
     *     Optional namespaced entity type key to filter by (e.g. {@code minecraft:block_display}).
     * @param bounded
     *     Whether the six bound coordinates constrain the query; when {@code false} they are ignored.
     * @param minX
     *     Minimum block X (inclusive) when bounded.
     * @param minY
     *     Minimum block Y (inclusive) when bounded.
     * @param minZ
     *     Minimum block Z (inclusive) when bounded.
     * @param maxX
     *     Maximum block X (inclusive) when bounded.
     * @param maxY
     *     Maximum block Y (inclusive) when bounded.
     * @param maxZ
     *     Maximum block Z (inclusive) when bounded.
     * @param countOnly
     *     When {@code true} the response carries only the count, skipping the per-entity payloads — the cheap
     *     probe shape for retrying assertions.
     */
    public record Command(
        String requestId,
        String worldName,
        @Nullable String entityTypeKey,
        boolean bounded,
        int minX,
        int minY,
        int minZ,
        int maxX,
        int maxY,
        int maxZ,
        boolean countOnly
    ) implements IAgentCommand<Response>
    {
        /**
         * Validates command inputs.
         */
        public Command
        {
            ProtocolPreconditions.requireNonBlank(requestId, "requestId");
            ProtocolPreconditions.requireNonBlank(worldName, "worldName");
            if (entityTypeKey != null && entityTypeKey.isBlank())
                throw new IllegalArgumentException("'entityTypeKey' must not be blank when present.");
            if (bounded && (minX > maxX || minY > maxY || minZ > maxZ))
                throw new IllegalArgumentException("Bounds must satisfy min <= max on every axis.");
        }

        @Override
        public Class<Response> responseType()
        {
            return Response.class;
        }
    }

    /**
     * A single entity's state, read in the same main-thread burst as its siblings.
     *
     * @param uuid
     *     The entity's UUID.
     * @param typeKey
     *     Namespaced entity type key.
     * @param x
     *     Position X.
     * @param y
     *     Position Y.
     * @param z
     *     Position Z.
     * @param customName
     *     The entity's custom name, or {@code null} when unnamed.
     * @param pdcKeys
     *     Namespaced keys present in the entity's persistent data container.
     * @param transform
     *     The display transformation for display entities, or {@code null} for all other entities.
     */
    public record EntityData(
        UUID uuid,
        String typeKey,
        double x,
        double y,
        double z,
        @Nullable String customName,
        List<String> pdcKeys,
        @Nullable TransformData transform
    )
    {
        /**
         * Validates and defensively copies the fields.
         */
        public EntityData
        {
            ProtocolPreconditions.requireNonNull(uuid, "uuid");
            ProtocolPreconditions.requireNonBlank(typeKey, "typeKey");
            pdcKeys = pdcKeys == null ? List.of() : List.copyOf(pdcKeys);
        }
    }

    /**
     * A display entity's transformation.
     *
     * @param translationX
     *     Translation X.
     * @param translationY
     *     Translation Y.
     * @param translationZ
     *     Translation Z.
     * @param scaleX
     *     Scale X.
     * @param scaleY
     *     Scale Y.
     * @param scaleZ
     *     Scale Z.
     * @param leftRotation
     *     Left rotation quaternion as {@code [x, y, z, w]}.
     * @param rightRotation
     *     Right rotation quaternion as {@code [x, y, z, w]}.
     */
    public record TransformData(
        double translationX,
        double translationY,
        double translationZ,
        double scaleX,
        double scaleY,
        double scaleZ,
        List<Double> leftRotation,
        List<Double> rightRotation
    )
    {
        /**
         * Validates and defensively copies the rotations.
         */
        public TransformData
        {
            leftRotation = leftRotation == null ? List.of() : List.copyOf(leftRotation);
            rightRotation = rightRotation == null ? List.of() : List.copyOf(rightRotation);
        }
    }

    /**
     * Response record for {@code QUERY_ENTITIES}.
     *
     * @param tick
     *     The server tick of the query burst; every entity in {@code entities} was read at this tick.
     * @param count
     *     Number of matching entities.
     * @param entities
     *     The matching entities' states; empty when the command was count-only.
     */
    public record Response(
        long tick,
        int count,
        List<EntityData> entities
    ) implements IAgentResponse
    {
        /**
         * Defensively copies the entity list.
         */
        public Response
        {
            entities = entities == null ? List.of() : List.copyOf(entities);
        }
    }
}
