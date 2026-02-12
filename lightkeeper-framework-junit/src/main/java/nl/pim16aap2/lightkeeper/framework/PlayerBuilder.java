package nl.pim16aap2.lightkeeper.framework;

/**
 * Fluent builder for synthetic players.
 */
public interface PlayerBuilder
{
    PlayerBuilder withName(String name);

    PlayerBuilder withRandomName();

    PlayerBuilder inWorld(WorldHandle world);

    PlayerBuilder atLocation(WorldHandle world, double x, double y, double z);

    PlayerBuilder atSpawn(WorldHandle world);

    PlayerBuilder withHealth(double health);

    PlayerBuilder withPermissions(String... permissions);

    PlayerHandle build();
}
