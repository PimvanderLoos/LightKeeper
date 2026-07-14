package nl.pim16aap2.lightkeeper.protocol;

/**
 * Outcome of a main-hand item drop, distinguishing the two reasons a drop can fail to materialize.
 */
public enum DropResult
{
    /**
     * The drop materialized: a {@code PlayerDropItemEvent} fired uncancelled, the item entity stays in the
     * world, and one item was consumed from the player's main hand.
     */
    DROPPED,

    /**
     * A plugin cancelled the {@code PlayerDropItemEvent}: the item entity was removed again and the player's
     * inventory is unchanged.
     */
    CANCELLED,

    /**
     * The player had nothing in their main hand; no event was fired at all.
     */
    EMPTY_HAND,
}
