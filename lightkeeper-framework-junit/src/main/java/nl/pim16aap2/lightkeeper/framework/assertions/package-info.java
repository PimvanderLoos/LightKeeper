/**
 * AssertJ-based assertions for live server/plugin, world/block, player/permission, and menu observations.
 *
 * <p>For example, {@code LightkeeperAssertions.assertThat(framework).server().plugin("MyPlugin").isEnabled()}
 * checks current plugin state. Assertions never wait implicitly; use {@code LightkeeperAssertions.eventually}
 * to retry a complete live observation.
 */
@org.jspecify.annotations.NullMarked
package nl.pim16aap2.lightkeeper.framework.assertions;
