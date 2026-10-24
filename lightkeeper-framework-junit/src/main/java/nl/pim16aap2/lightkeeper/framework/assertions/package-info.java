/**
 * AssertJ assertions for live server/plugin, world/block, entity-query, player, menu, event-capture and error state,
 * plus frozen inventory/item, event/value, result, entity/geometry and semantic chat snapshots.
 *
 * <p>For example, this checks current plugin state:
 * <pre>{@code
 * LightkeeperAssertions.assertThat(framework)
 *     .server()
 *     .plugin("MyPlugin")
 *     .isEnabled();
 * }</pre>
 *
 * <p>This freezes an inventory observation and selects its item:
 * <pre>{@code
 * LightkeeperAssertions.assertThat(player)
 *     .inventory()
 *     .itemAt(0);
 * }</pre>
 *
 * <p>Assertions never wait implicitly; use
 * {@code LightkeeperAssertions.eventually} to retry a complete observation, recreating frozen navigation inside
 * the callback. Actions, fixture creation and buffer clearing remain outside assertion chains.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.framework.assertions;
