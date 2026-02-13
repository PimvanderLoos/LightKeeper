package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.MenuHandle;
import nl.pim16aap2.lightkeeper.framework.PlayerHandle;
import nl.pim16aap2.lightkeeper.framework.Vector3Di;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AbstractStringAssert;
import org.assertj.core.api.Assertions;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Objects;

/**
 * AssertJ entrypoints for LightKeeper handles.
 */
public final class LightkeeperAssertions extends Assertions
{
    private LightkeeperAssertions()
    {
    }

    /**
     * Creates an assertion chain for world-related state.
     *
     * @param actual
     *     World handle under test.
     * @return World assertion entrypoint.
     */
    public static WorldHandleAssert assertThat(WorldHandle actual)
    {
        return new WorldHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for menu-related state.
     *
     * @param actual
     *     Menu handle under test.
     * @return Menu assertion entrypoint.
     */
    public static MenuHandleAssert assertThat(MenuHandle actual)
    {
        return new MenuHandleAssert(actual);
    }

    /**
     * Creates an assertion chain for player-related state.
     *
     * @param actual
     *     Player handle under test.
     * @return Player assertion entrypoint.
     */
    public static PlayerHandleAssert assertThat(PlayerHandle actual)
    {
        return new PlayerHandleAssert(actual);
    }

    /**
     * Assertions for world handles.
     */
    public static final class WorldHandleAssert extends AbstractAssert<WorldHandleAssert, WorldHandle>
    {
        private WorldHandleAssert(WorldHandle actual)
        {
            super(actual, WorldHandleAssert.class);
        }

        /**
         * Starts an assertion chain for a block at the requested coordinates.
         *
         * @param x
         *     Block X coordinate.
         * @param y
         *     Block Y coordinate.
         * @param z
         *     Block Z coordinate.
         * @return Block assertion entrypoint.
         */
        public WorldBlockAssert hasBlockAt(int x, int y, int z)
        {
            isNotNull();
            return new WorldBlockAssert(actual, new Vector3Di(x, y, z));
        }
    }

    /**
     * Assertions for a specific block position in a world.
     */
    public static final class WorldBlockAssert extends AbstractAssert<WorldBlockAssert, WorldHandle>
    {
        private final Vector3Di position;

        private WorldBlockAssert(WorldHandle worldHandle, Vector3Di position)
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
            final String actualMaterial = normalizeMaterial(actual.blockTypeAt(position));
            final String expectedMaterial = normalizeMaterial(materialKey);
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

    /**
     * Assertions for open menu state.
     */
    public static final class MenuHandleAssert extends AbstractAssert<MenuHandleAssert, MenuHandle>
    {
        private MenuHandleAssert(MenuHandle actual)
        {
            super(actual, MenuHandleAssert.class);
        }

        /**
         * Asserts that an open menu exists and has the requested title.
         *
         * @param expectedTitle
         *     Expected menu title.
         * @return This assertion for fluent chaining.
         */
        public MenuHandleAssert hasTitle(String expectedTitle)
        {
            isNotNull();
            if (!actual.hasTitle(expectedTitle))
            {
                failWithMessage("Expected open menu title '%s'.", expectedTitle);
            }
            return this;
        }

        /**
         * Asserts that a menu slot contains the requested material.
         *
         * @param slot
         *     Slot index.
         * @param materialKey
         *     Expected material key.
         * @return This assertion for fluent chaining.
         */
        public MenuHandleAssert hasItemAt(int slot, String materialKey)
        {
            isNotNull();
            if (!actual.hasItemAt(slot, materialKey))
            {
                failWithMessage("Expected menu item at slot %d with material '%s'.", slot, materialKey);
            }
            return this;
        }

        /**
         * Asserts that a menu slot matches the requested Bukkit item stack snapshot.
         *
         * @param slot
         *     Slot index.
         * @param itemStack
         *     Expected item stack.
         * @return This assertion for fluent chaining.
         */
        public MenuHandleAssert hasItemAt(int slot, ItemStack itemStack)
        {
            isNotNull();
            if (!actual.hasItemAt(slot, itemStack))
            {
                failWithMessage("Expected menu item at slot %d with expected ItemStack.", slot);
            }
            return this;
        }
    }

    /**
     * Assertions for synthetic player state.
     */
    public static final class PlayerHandleAssert extends AbstractAssert<PlayerHandleAssert, PlayerHandle>
    {
        private PlayerHandleAssert(PlayerHandle actual)
        {
            super(actual, PlayerHandleAssert.class);
        }

        /**
         * Asserts that the player has the expected name.
         *
         * @param expectedName
         *     Expected player name.
         * @return This assertion for fluent chaining.
         */
        public PlayerHandleAssert hasName(String expectedName)
        {
            isNotNull();
            if (!Objects.equals(actual.name(), expectedName))
                failWithMessage("Expected player name '%s' but was '%s'.", expectedName, actual.name());
            return this;
        }

        /**
         * Asserts that at least one received message contains the requested text fragment.
         *
         * @param expectedFragment
         *     Required message fragment.
         * @return This assertion for fluent chaining.
         */
        public PlayerHandleAssert receivedMessage(String expectedFragment)
        {
            isNotNull();
            Assertions.assertThat(actual.receivedMessagesText())
                .contains(expectedFragment);
            return this;
        }

        /**
         * Returns an AssertJ string assertion over all received messages concatenated into one text block.
         *
         * @return AssertJ string assertion.
         */
        public AbstractStringAssert<?> receivedMessagesText()
        {
            isNotNull();
            return Assertions.assertThat(actual.receivedMessagesText());
        }
    }

    private static String normalizeMaterial(String materialKey)
    {
        final String trimmed = materialKey.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("minecraft:") ? trimmed : "minecraft:" + trimmed;
    }
}
