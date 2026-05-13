package nl.pim16aap2.lightkeeper.framework;

/**
 * Snapshot of a chat component.
 *
 * @param json
 *     The raw JSON representation of the component.
 */
public record ChatComponentSnapshot(
    String json
)
{
}
