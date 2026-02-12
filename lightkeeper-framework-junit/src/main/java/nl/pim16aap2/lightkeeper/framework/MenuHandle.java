package nl.pim16aap2.lightkeeper.framework;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Handle for an open player menu.
 */
public final class MenuHandle
{
    private static final Duration DEFAULT_MENU_WAIT_TIMEOUT = Duration.ofSeconds(10);

    private final LightkeeperFramework framework;
    private final PlayerHandle player;

    public MenuHandle(LightkeeperFramework framework, PlayerHandle player)
    {
        this.framework = Objects.requireNonNull(framework, "framework may not be null.");
        this.player = Objects.requireNonNull(player, "player may not be null.");
    }

    public PlayerHandle player()
    {
        return player;
    }

    public MenuSnapshot snapshot()
    {
        return framework.menuSnapshot(player);
    }

    public MenuHandle verifyMenuName(String expectedTitle)
    {
        final MenuSnapshot snapshot = snapshot();
        if (!snapshot.open())
            throw new IllegalStateException("Expected an open menu, but menu is closed.");
        if (!Objects.equals(snapshot.title(), expectedTitle))
        {
            throw new IllegalStateException(
                "Expected menu title '%s' but got '%s'."
                    .formatted(expectedTitle, snapshot.title())
            );
        }
        return this;
    }

    public MenuHandle clickAtIndex(int slot)
    {
        framework.clickMenuSlot(player, slot);
        return this;
    }

    public MenuHandle dragWithMaterial(String materialKey, int... slots)
    {
        framework.dragMenuSlots(player, materialKey, slots);
        return this;
    }

    public MenuHandle andWaitTicks(int ticks)
    {
        framework.waitTicks(ticks);
        return this;
    }

    public MenuHandle andWaitForMenuClose()
    {
        framework.waitUntil(() -> !snapshot().open(), DEFAULT_MENU_WAIT_TIMEOUT);
        return this;
    }

    public MenuHandle andWaitForMenuClose(Duration timeout)
    {
        framework.waitUntil(() -> !snapshot().open(), timeout);
        return this;
    }

    public MenuHandle verifyMenuClosed()
    {
        if (snapshot().open())
            throw new IllegalStateException("Expected menu to be closed.");
        return this;
    }

    public boolean hasTitle(String expectedTitle)
    {
        final MenuSnapshot snapshot = snapshot();
        return snapshot.open() && Objects.equals(snapshot.title(), expectedTitle);
    }

    public boolean hasItemAt(int slot, String materialKey)
    {
        final Optional<MenuItemSnapshot> itemSnapshot = findItem(slot);
        return itemSnapshot
            .map(item -> normalizeMaterialKey(item.materialKey()).equals(normalizeMaterialKey(materialKey)))
            .orElse(false);
    }

    public boolean hasItemAt(int slot, ItemStack itemStack)
    {
        final Optional<MenuItemSnapshot> itemSnapshot = findItem(slot);
        if (itemSnapshot.isEmpty())
            return false;

        final MenuItemSnapshot actual = itemSnapshot.get();
        if (!normalizeMaterialKey(actual.materialKey())
            .equals(normalizeMaterialKey(itemStack.getType().getKey().toString())))
            return false;

        final @Nullable String expectedName = itemStack.getItemMeta() == null ? null : itemStack.getItemMeta().getDisplayName();
        final @Nullable List<String> expectedLore = itemStack.getItemMeta() == null ? null : itemStack.getItemMeta().getLore();
        if (!Objects.equals(emptyToNull(actual.displayName()), emptyToNull(expectedName)))
            return false;
        return Objects.equals(emptyListToNull(actual.lore()), emptyListToNull(expectedLore));
    }

    private Optional<MenuItemSnapshot> findItem(int slot)
    {
        return snapshot().items().stream()
            .filter(item -> item.slot() == slot)
            .findFirst();
    }

    private static String normalizeMaterialKey(String materialKey)
    {
        final String trimmed = materialKey.trim().toLowerCase();
        return trimmed.startsWith("minecraft:") ? trimmed : "minecraft:" + trimmed;
    }

    private static @Nullable String emptyToNull(@Nullable String value)
    {
        if (value == null || value.isBlank())
            return null;
        return value;
    }

    private static @Nullable List<String> emptyListToNull(@Nullable List<String> value)
    {
        if (value == null || value.isEmpty())
            return null;
        return value;
    }
}
