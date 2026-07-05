package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Material;

/**
 * Material classification helpers shared by agent action handlers.
 *
 * <p>Centralises predicates that need to behave identically across handlers (e.g. {@link AgentMenuActions}
 * and {@link AgentPlayerStateActions}). Avoids invoking {@link Material#isAir()} directly because that delegates
 * through {@code Material.asBlockType()}, which forces initialisation of {@code org.bukkit.Registry} and
 * makes the call hostile to unit tests that do not boot a Bukkit server.
 */
final class AgentMaterials
{
    private AgentMaterials()
    {
    }

    /**
     * Determines whether the given material represents any in-game air block.
     *
     * @param material
     *     Material to classify; must not be {@code null}.
     * @return
     *     {@code true} when the material is {@link Material#AIR}, {@link Material#CAVE_AIR}, or
     *     {@link Material#VOID_AIR}; {@code false} otherwise.
     */
    static boolean isAir(Material material)
    {
        return material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR;
    }
}
