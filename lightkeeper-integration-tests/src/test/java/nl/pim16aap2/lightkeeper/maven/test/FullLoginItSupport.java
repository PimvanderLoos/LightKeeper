package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.CapturedEventSnapshot;
import nl.pim16aap2.lightkeeper.framework.EntitySnapshot;
import nl.pim16aap2.lightkeeper.framework.Vec3;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import nl.pim16aap2.lightkeeper.protocol.IProtocolValue;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Shared assertions and lookups for the FULL_LOGIN validation-matrix integration tests.
 *
 * <p>Kept as plain static helpers so both matrix classes ({@code LightkeeperFullLoginValidationIT} and
 * {@code LightkeeperFullLoginSessionIT}) assert identities, positions, and captured events the same way.
 */
final class FullLoginItSupport
{
    private FullLoginItSupport()
    {
    }

    /**
     * Computes the offline-mode UUID the server derives from a player name during a {@code FULL_LOGIN} join.
     *
     * @param name
     *     The player name.
     * @return The server-derived offline UUID.
     */
    static UUID offlineUuid(String name)
    {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Looks up a player's current position through the live entity query.
     *
     * @param world
     *     World to query.
     * @param playerId
     *     UUID of the player to find.
     * @return The player's position, or empty when the player is not online in the world.
     */
    static Optional<Vec3> playerPosition(WorldHandle world, UUID playerId)
    {
        return world.entities().ofType("minecraft:player").snapshot().stream()
            .filter(snapshot -> snapshot.uuid().equals(playerId))
            .map(EntitySnapshot::position)
            .findFirst();
    }

    /**
     * Asserts that a position matches the expected coordinates within half a block on every axis.
     *
     * @param actual
     *     Observed position.
     * @param expected
     *     Expected position.
     */
    static void assertPositionCloseTo(Vec3 actual, Vec3 expected)
    {
        assertThat(actual.x()).isCloseTo(expected.x(), within(0.5));
        assertThat(actual.y()).isCloseTo(expected.y(), within(0.5));
        assertThat(actual.z()).isCloseTo(expected.z(), within(0.5));
    }

    /**
     * Filters captured events down to those whose {@code getPlayer} value references the given player.
     *
     * @param events
     *     Captured event snapshots.
     * @param playerId
     *     UUID of the acting player.
     * @return The events attributed to the player, in capture order.
     */
    static List<CapturedEventSnapshot> eventsWithPlayerRef(List<CapturedEventSnapshot> events, UUID playerId)
    {
        return events.stream()
            .filter(event -> event.value("getPlayer") instanceof IProtocolValue.PRef playerRef
                && playerRef.id().equals(playerId.toString()))
            .toList();
    }
}
