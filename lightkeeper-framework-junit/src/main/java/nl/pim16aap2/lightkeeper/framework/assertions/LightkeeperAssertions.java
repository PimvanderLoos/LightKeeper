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

import java.util.Objects;

/**
 * AssertJ entrypoints for LightKeeper handles.
 */
public final class LightkeeperAssertions extends Assertions
{
    private LightkeeperAssertions()
    {
    }

    public static WorldHandleAssert assertThat(WorldHandle actual)
    {
        return new WorldHandleAssert(actual);
    }

    public static MenuHandleAssert assertThat(MenuHandle actual)
    {
        return new MenuHandleAssert(actual);
    }

    public static PlayerHandleAssert assertThat(PlayerHandle actual)
    {
        return new PlayerHandleAssert(actual);
    }

    public static final class WorldHandleAssert extends AbstractAssert<WorldHandleAssert, WorldHandle>
    {
        private WorldHandleAssert(WorldHandle actual)
        {
            super(actual, WorldHandleAssert.class);
        }

        public WorldBlockAssert hasBlockAt(int x, int y, int z)
        {
            isNotNull();
            return new WorldBlockAssert(actual, new Vector3Di(x, y, z));
        }
    }

    public static final class WorldBlockAssert extends AbstractAssert<WorldBlockAssert, WorldHandle>
    {
        private final Vector3Di position;

        private WorldBlockAssert(WorldHandle worldHandle, Vector3Di position)
        {
            super(worldHandle, WorldBlockAssert.class);
            this.position = position;
        }

        public WorldBlockAssert ofType(Material material)
        {
            return ofType(material.getKey().toString());
        }

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

    public static final class MenuHandleAssert extends AbstractAssert<MenuHandleAssert, MenuHandle>
    {
        private MenuHandleAssert(MenuHandle actual)
        {
            super(actual, MenuHandleAssert.class);
        }

        public MenuHandleAssert hasTitle(String expectedTitle)
        {
            isNotNull();
            if (!actual.hasTitle(expectedTitle))
            {
                failWithMessage("Expected open menu title '%s'.", expectedTitle);
            }
            return this;
        }

        public MenuHandleAssert hasItemAt(int slot, String materialKey)
        {
            isNotNull();
            if (!actual.hasItemAt(slot, materialKey))
            {
                failWithMessage("Expected menu item at slot %d with material '%s'.", slot, materialKey);
            }
            return this;
        }

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

    public static final class PlayerHandleAssert extends AbstractAssert<PlayerHandleAssert, PlayerHandle>
    {
        private PlayerHandleAssert(PlayerHandle actual)
        {
            super(actual, PlayerHandleAssert.class);
        }

        public PlayerHandleAssert hasName(String expectedName)
        {
            isNotNull();
            if (!Objects.equals(actual.name(), expectedName))
                failWithMessage("Expected player name '%s' but was '%s'.", expectedName, actual.name());
            return this;
        }

        public PlayerHandleAssert receivedMessage(String expectedFragment)
        {
            isNotNull();
            Assertions.assertThat(actual.receivedMessagesText())
                .contains(expectedFragment);
            return this;
        }

        public AbstractStringAssert<?> receivedMessagesText()
        {
            isNotNull();
            return Assertions.assertThat(actual.receivedMessagesText());
        }
    }

    private static String normalizeMaterial(String materialKey)
    {
        final String trimmed = materialKey.trim().toLowerCase();
        return trimmed.startsWith("minecraft:") ? trimmed : "minecraft:" + trimmed;
    }
}
