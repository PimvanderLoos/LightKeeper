package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.framework.internal.IFrameworkGateway;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Handle for an open player menu.
 */
public final class MenuHandle
{
    private static final Duration DEFAULT_MENU_WAIT_TIMEOUT = Duration.ofSeconds(10);

    private final IFrameworkGateway frameworkGateway;
    private final PlayerHandle player;

    /**
     * Creates a menu handle bound to a player.
     *
     * @param frameworkGateway
     *     Internal framework gateway.
     * @param player
     *     Owning player.
     */
    public MenuHandle(IFrameworkGateway frameworkGateway, PlayerHandle player)
    {
        this.frameworkGateway = Objects.requireNonNull(frameworkGateway, "frameworkGateway may not be null.");
        this.player = Objects.requireNonNull(player, "player may not be null.");
    }

    /**
     * Gets the player associated with this menu handle.
     *
     * @return The associated player.
     */
    public PlayerHandle player()
    {
        return player;
    }

    /**
     * Retrieves the latest menu snapshot.
     *
     * @return The current menu snapshot.
     */
    public MenuSnapshot snapshot()
    {
        return frameworkGateway.menuSnapshot(player.uniqueId());
    }

    /**
     * Asserts that the menu is open and has the expected title.
     *
     * @param expectedTitle
     *     Expected title text.
     * @return This handle.
     */
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

    /**
     * Clicks a slot in the open menu.
     *
     * @param slot
     *     Inventory slot index.
     * @return This handle.
     */
    public MenuHandle clickAtIndex(int slot)
    {
        frameworkGateway.clickMenuSlot(player.uniqueId(), slot);
        return this;
    }

    /**
     * Performs a drag operation using a material over the provided slots.
     *
     * @param materialKey
     *     Material identifier.
     * @param slots
     *     Target slot indices.
     * @return This handle.
     */
    public MenuHandle dragWithMaterial(String materialKey, int... slots)
    {
        frameworkGateway.dragMenuSlots(player.uniqueId(), materialKey, slots);
        return this;
    }

    /**
     * Waits for the requested number of ticks.
     *
     * @param ticks
     *     Tick count.
     * @return This handle.
     */
    public MenuHandle andWaitTicks(int ticks)
    {
        frameworkGateway.waitTicks(ticks);
        return this;
    }

    /**
     * Waits until the menu closes using the default timeout.
     *
     * @return This handle.
     */
    public MenuHandle andWaitForMenuClose()
    {
        frameworkGateway.waitUntil(() -> !snapshot().open(), DEFAULT_MENU_WAIT_TIMEOUT);
        return this;
    }

    /**
     * Waits until the menu closes.
     *
     * @param timeout
     *     Maximum wait duration.
     * @return This handle.
     */
    public MenuHandle andWaitForMenuClose(Duration timeout)
    {
        frameworkGateway.waitUntil(() -> !snapshot().open(), timeout);
        return this;
    }

    /**
     * Asserts that no menu is currently open.
     *
     * @return This handle.
     */
    public MenuHandle verifyMenuClosed()
    {
        if (snapshot().open())
            throw new IllegalStateException("Expected menu to be closed.");
        return this;
    }

    /**
     * Checks whether the current open menu has the expected title.
     *
     * @param expectedTitle
     *     Expected title text.
     * @return {@code true} when the menu is open with the expected title.
     */
    public boolean hasTitle(String expectedTitle)
    {
        final MenuSnapshot snapshot = snapshot();
        return snapshot.open() && Objects.equals(snapshot.title(), expectedTitle);
    }

    /**
     * Checks whether a slot contains a matching material key.
     *
     * @param slot
     *     Inventory slot index.
     * @param materialKey
     *     Expected material key.
     * @return {@code true} when the slot matches.
     */
    public boolean hasItemAt(int slot, String materialKey)
    {
        final Optional<MenuItemSnapshot> itemSnapshot = findItem(slot);
        return itemSnapshot
            .map(item -> normalizeMaterialKey(item.materialKey()).equals(normalizeMaterialKey(materialKey)))
            .orElse(false);
    }

    /**
     * Checks whether a slot matches an expected item stack snapshot.
     *
     * @param slot
     *     Inventory slot index.
     * @param itemStack
     *     Expected item stack.
     * @return {@code true} when the slot matches.
     */
    public boolean hasItemAt(int slot, ItemStack itemStack)
    {
        final Optional<MenuItemSnapshot> itemSnapshot = findItem(slot);
        if (itemSnapshot.isEmpty())
            return false;

        final MenuItemSnapshot actual = itemSnapshot.get();
        if (!normalizeMaterialKey(actual.materialKey())
            .equals(normalizeMaterialKey(itemStack.getType().getKey().toString())))
            return false;

        final @Nullable String expectedName = itemStack.getItemMeta() == null
            ? null
            : itemStack.getItemMeta().getDisplayName();
        final @Nullable List<String> expectedLore = itemStack.getItemMeta() == null
            ? null
            : itemStack.getItemMeta().getLore();
        if (!Objects.equals(emptyToNull(actual.displayName()), emptyToNull(expectedName)))
            return false;
        return Objects.equals(normalizeLore(actual.lore()), normalizeLore(expectedLore));
    }

    private Optional<MenuItemSnapshot> findItem(int slot)
    {
        return snapshot().items().stream()
            .filter(item -> item.slot() == slot)
            .findFirst();
    }

    private static String normalizeMaterialKey(String materialKey)
    {
        final String trimmed = materialKey.trim().toLowerCase(Locale.ROOT);
        return trimmed.startsWith("minecraft:") ? trimmed : "minecraft:" + trimmed;
    }

    private static @Nullable String emptyToNull(@Nullable String value)
    {
        if (value == null || value.isBlank())
            return null;
        return value;
    }

    private static List<String> normalizeLore(@Nullable List<String> value)
    {
        if (value == null || value.isEmpty())
            return List.of();
        return value;
    }
}
