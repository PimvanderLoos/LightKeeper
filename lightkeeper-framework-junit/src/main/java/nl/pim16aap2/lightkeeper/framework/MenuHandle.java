package nl.pim16aap2.lightkeeper.framework;

import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Handle for an open player menu.
 * <p>
 * Menu actions ({@link #clickAtIndex(int)}, {@link #clickItem(String)}, {@link #dragWithMaterial}) auto-wait for
 * an open menu (bounded, default 10 seconds) before acting. This is the one deliberate implicit wait in the
 * framework API: waiting for the menu is the only correct behavior before interacting with it. Probes and
 * verifications (e.g. {@link #isOpen()}, {@link #snapshot()}, {@link #verifyMenuName}, {@link #verifyMenuClosed},
 * {@link #hasTitle}, {@link #hasItemAt}) never wait; the explicit {@link #andWaitForMenuClose()} waits for the
 * opposite transition.
 */
public final class MenuHandle
{
    /**
     * Default bound for menu-related waits; shared with {@link PlayerHandle}'s menu-open waits.
     */
    static final Duration DEFAULT_MENU_WAIT_TIMEOUT = Duration.ofSeconds(10);

    private final IFrameworkGatewayView frameworkGateway;
    private final PlayerHandle player;

    /**
     * Creates a menu handle bound to a player.
     *
     * @param frameworkGateway
     *     Internal framework gateway.
     * @param player
     *     Owning player.
     */
    MenuHandle(IFrameworkGatewayView frameworkGateway, PlayerHandle player)
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
     * Checks whether an actionable menu is currently open. An instant probe suitable for
     * {@code waitUntil(...)} conditions; never waits.
     *
     * @return {@code true} when a menu is open.
     */
    public boolean isOpen()
    {
        return snapshot().open();
    }

    /**
     * Clicks a slot in the open menu, auto-waiting for an open menu first (bounded, default 10 seconds).
     *
     * @param slot
     *     Inventory slot index.
     * @return This handle.
     * @throws IllegalStateException
     *     When no menu opens within the wait bound.
     */
    public MenuHandle clickAtIndex(int slot)
    {
        awaitOpenMenu();
        frameworkGateway.clickMenuSlot(player.uniqueId(), slot);
        return this;
    }

    /**
     * Retrieves the open menu's snapshot after {@link #awaitOpenMenu()}, distinguishing a menu that closed again
     * mid-flight from a menu that simply lacks the requested item.
     *
     * @return The open menu's snapshot.
     * @throws IllegalStateException
     *     When the menu closed between the wait passing and the snapshot.
     */
    private MenuSnapshot openSnapshotAfterWait()
    {
        final MenuSnapshot openSnapshot = snapshot();
        if (!openSnapshot.open())
            throw new IllegalStateException(
                "The menu for player '%s' closed while it was being interacted with.".formatted(player.name()));
        return openSnapshot;
    }

    /**
     * Clicks the first menu item whose display name contains the given fragment, auto-waiting for an open menu
     * first (bounded, default 10 seconds).
     *
     * @param displayNameFragment
     *     Fragment to match against item display names (case-sensitive contains).
     * @return This handle.
     * @throws IllegalStateException
     *     When no menu opens within the wait bound, or no item in the open menu matches the fragment.
     */
    public MenuHandle clickItem(String displayNameFragment)
    {
        final String trimmedFragment =
            Objects.requireNonNull(displayNameFragment, "displayNameFragment may not be null.").trim();
        if (trimmedFragment.isEmpty())
            throw new IllegalArgumentException("displayNameFragment may not be blank.");

        awaitOpenMenu();
        final MenuSnapshot openSnapshot = openSnapshotAfterWait();
        final MenuItemSnapshot item = openSnapshot.items().stream()
            .filter(candidate -> candidate.displayName().contains(trimmedFragment))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No item with a display name containing '%s' in menu '%s'. Items: %s".formatted(
                    trimmedFragment,
                    openSnapshot.title(),
                    openSnapshot.items().stream()
                        .map(candidate -> "'" + candidate.displayName() + "'")
                        .toList()
                )));
        frameworkGateway.clickMenuSlot(player.uniqueId(), item.slot());
        return this;
    }

    /**
     * Performs a drag operation using a material over the provided slots, auto-waiting for an open menu first
     * (bounded, default 10 seconds).
     *
     * @param materialKey
     *     Material identifier.
     * @param slots
     *     Target slot indices.
     * @return This handle.
     * @throws IllegalStateException
     *     When no menu opens within the wait bound.
     */
    public MenuHandle dragWithMaterial(String materialKey, int... slots)
    {
        awaitOpenMenu();
        frameworkGateway.dragMenuSlots(player.uniqueId(), materialKey, slots);
        return this;
    }

    /**
     * Waits for an open menu within the default bound.
     *
     * @throws IllegalStateException
     *     When no menu opens within the wait bound.
     */
    private void awaitOpenMenu()
    {
        try
        {
            frameworkGateway.waitUntil(() -> snapshot().open(), DEFAULT_MENU_WAIT_TIMEOUT);
        }
        catch (IllegalStateException exception)
        {
            final MenuSnapshot lastSnapshot = snapshot();
            throw new IllegalStateException(
                "No open menu for player '%s' within %s (open=%s, title='%s').".formatted(
                    player.name(), DEFAULT_MENU_WAIT_TIMEOUT, lastSnapshot.open(), lastSnapshot.title()),
                exception);
        }
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
