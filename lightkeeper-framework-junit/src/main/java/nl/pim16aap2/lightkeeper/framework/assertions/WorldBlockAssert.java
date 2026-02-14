package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.AbstractAssert;
import org.bukkit.Material;

import java.util.Objects;

/**
 * Assertions for a specific block position in a world.
 */
public final class WorldBlockAssert extends AbstractAssert<WorldBlockAssert, WorldHandle>
{
    private final Vector3Di position;

    WorldBlockAssert(WorldHandle worldHandle, Vector3Di position)
    {
        super(worldHandle, WorldBlockAssert.class);
        this.position = position;
    }

    /**
     * Asserts that the block has the expected Bukkit material.
     *
     * @param material
     *     Expected material.
     * @return This assertion for fluent chaining.
     */
    public WorldBlockAssert ofType(Material material)
    {
        return ofType(material.getKey().toString());
    }

    /**
     * Asserts that the block has the expected namespaced material key.
     *
     * @param materialKey
     *     Expected material key (for example {@code minecraft:stone}).
     * @return This assertion for fluent chaining.
     */
    public WorldBlockAssert ofType(String materialKey)
    {
        isNotNull();
        final String actualMaterial = MaterialKeyNormalizer.normalizeMaterial(actual.blockTypeAt(position));
        final String expectedMaterial = MaterialKeyNormalizer.normalizeMaterial(materialKey);
        if (!Objects.equals(actualMaterial, expectedMaterial))
        {
            failWithMessage(
                "Expected block at (%d,%d,%d) to be '%s' but was '%s'.",
                position.x(),
                position.y(),
                position.z(),
                expectedMaterial,
                actualMaterial
            );
        }
        return this;
    }
}
