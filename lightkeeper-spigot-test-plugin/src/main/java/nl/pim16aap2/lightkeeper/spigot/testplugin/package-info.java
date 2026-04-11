/**
 * Standalone Spigot test plugin used by LightKeeper integration suites.
 *
 * <p>The package owns the test-facing {@code /lktestgui} flow and InventoryGUI-backed menus that integration tests
 * interact with through synthetic players and menu RPC actions.</p>
 *
 * <p><strong>What this package contains</strong>:
 * <ul>
 *   <li>{@link nl.pim16aap2.lightkeeper.spigot.testplugin.LightkeeperSpigotTestPlugin} as plugin entrypoint and
 *       event registration root.</li>
 *   <li>{@link nl.pim16aap2.lightkeeper.spigot.testplugin.LkTestGuiCommandExecutor} for command permission checks
 *       and menu launch.</li>
 *   <li>{@link nl.pim16aap2.lightkeeper.spigot.testplugin.GuiMenuService} for menu definitions and transitions.</li>
 * </ul>
 *
 * <p><strong>Example flow</strong>: a player runs {@code /lktestgui}, the main menu opens, slot {@code 0} moves to a
 * sub menu, and slot {@code 2} closes the GUI.</p>
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.spigot.testplugin;
