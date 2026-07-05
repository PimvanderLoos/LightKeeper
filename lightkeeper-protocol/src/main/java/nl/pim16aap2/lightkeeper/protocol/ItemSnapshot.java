package nl.pim16aap2.lightkeeper.protocol;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Typed snapshot of a single non-air inventory or menu slot, carried directly inside protocol responses instead
 * of a pre-serialized JSON string.
 *
 * @param slot
 *     Raw slot index.
 * @param materialKey
 *     Namespaced material key (e.g. {@code "minecraft:stone"}).
 * @param displayName
 *     Custom display name, or {@code null} when the item has none.
 * @param lore
 *     Lore lines; never {@code null}.
 */
public record ItemSnapshot(
    int slot,
    String materialKey,
    @Nullable String displayName,
    List<String> lore
)
{
    /**
     * Validates the material key and defensively copies the lore.
     */
    public ItemSnapshot
    {
        ProtocolPreconditions.requireNonBlank(materialKey, "materialKey");
        lore = lore == null ? List.of() : List.copyOf(lore);
    }
}
