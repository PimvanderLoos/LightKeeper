package nl.pim16aap2.lightkeeper.framework;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Frozen snapshot of one entity's state, read in the same main-thread burst as its query siblings — every
 * snapshot from one {@link EntityQuery#snapshot()} call shares one tick and is internally consistent.
 *
 * @param uuid
 *     The entity's UUID.
 * @param typeKey
 *     Namespaced entity type key, e.g. {@code minecraft:block_display}.
 * @param position
 *     The entity's position at the burst tick.
 * @param customName
 *     The entity's custom name, or {@code null} when unnamed.
 * @param pdcKeys
 *     Namespaced keys present in the entity's persistent data container, sorted.
 * @param transform
 *     The display transformation for display entities, or {@code null} for all other entities.
 * @param tick
 *     The server tick of the query burst.
 */
public record EntitySnapshot(
    UUID uuid,
    String typeKey,
    Vec3 position,
    @Nullable String customName,
    List<String> pdcKeys,
    @Nullable Transform transform,
    long tick
)
{
    /**
     * Validates and defensively copies the fields; sorts {@code pdcKeys} so the documented ordering holds
     * regardless of the source.
     */
    public EntitySnapshot
    {
        Objects.requireNonNull(uuid, "uuid may not be null.");
        Objects.requireNonNull(typeKey, "typeKey may not be null.");
        Objects.requireNonNull(position, "position may not be null.");
        pdcKeys = pdcKeys == null ? List.of() : pdcKeys.stream().sorted().toList();
    }

    /**
     * A display entity's transformation: display entities move via transform, not position.
     *
     * @param translation
     *     The translation component.
     * @param scale
     *     The scale component.
     * @param leftRotation
     *     Left rotation quaternion.
     * @param rightRotation
     *     Right rotation quaternion.
     */
    public record Transform(
        Vec3 translation,
        Vec3 scale,
        Rotation leftRotation,
        Rotation rightRotation
    )
    {
        /**
         * Validates the components.
         */
        public Transform
        {
            Objects.requireNonNull(translation, "translation may not be null.");
            Objects.requireNonNull(scale, "scale may not be null.");
            Objects.requireNonNull(leftRotation, "leftRotation may not be null.");
            Objects.requireNonNull(rightRotation, "rightRotation may not be null.");
        }
    }

    /**
     * A rotation quaternion.
     *
     * @param x
     *     X component.
     * @param y
     *     Y component.
     * @param z
     *     Z component.
     * @param w
     *     W component.
     */
    public record Rotation(double x, double y, double z, double w)
    {
    }
}
