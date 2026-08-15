package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.damage.DamageSource;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

/**
 * Observes Bukkit lifecycle transitions for LightKeeper-managed synthetic players.
 *
 * <p>Deaths and disconnects are retained in {@link AgentSyntheticPlayerStore} so a later framework action can
 * report the lifecycle cause rather than an ambiguous Bukkit rejection. Events for ordinary server players are
 * ignored.
 */
final class AgentSyntheticPlayerLifecycleListener implements Listener
{
    private final AgentSyntheticPlayerStore playerStore;
    private final Logger logger;

    /**
     * @param playerStore
     *     Synthetic-player registry whose lifecycle state is updated.
     * @param logger
     *     Agent logger used for lifecycle diagnostics.
     */
    AgentSyntheticPlayerLifecycleListener(AgentSyntheticPlayerStore playerStore, Logger logger)
    {
        this.playerStore = Objects.requireNonNull(playerStore, "playerStore");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Records and logs a synthetic-player death.
     *
     * @param event
     *     Bukkit death event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerDeath(PlayerDeathEvent event)
    {
        final Player player = event.getEntity();
        if (!playerStore.isSyntheticPlayer(player.getUniqueId()))
            return;

        final String details = "player died; cause=%s location=%s deathMessage=%s".formatted(
            formatDamageSource(event.getDamageSource()),
            formatLocation(player.getLocation()),
            Objects.requireNonNullElse(event.getDeathMessage(), "<none>")
        );
        playerStore.markPlayerDead(player.getUniqueId(), details);
        logger.info(
            "LK_AGENT: Synthetic player '%s' (%s) died: %s"
                .formatted(player.getName(), player.getUniqueId(), details));
    }

    /**
     * Clears a recorded death once Bukkit has respawned the player.
     *
     * @param event
     *     Bukkit respawn event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerRespawn(PlayerRespawnEvent event)
    {
        final Player player = event.getPlayer();
        if (!playerStore.isSyntheticPlayer(player.getUniqueId()))
            return;
        playerStore.markPlayerRespawned(player.getUniqueId());
        logger.info(
            "LK_AGENT: Synthetic player '%s' (%s) respawned at %s."
                .formatted(player.getName(), player.getUniqueId(), formatLocation(event.getRespawnLocation())));
    }

    /**
     * Records the authoritative quit transition after the player has left the server.
     *
     * @param event
     *     Bukkit quit event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerQuit(PlayerQuitEvent event)
    {
        final Player player = event.getPlayer();
        if (!playerStore.isSyntheticPlayer(player.getUniqueId()))
            return;

        final String details = "player disconnected; quitMessage=%s"
            .formatted(Objects.requireNonNullElse(event.getQuitMessage(), "<none>"));
        playerStore.markPlayerDisconnected(player.getUniqueId(), details);
        logger.info(
            "LK_AGENT: Synthetic player '%s' (%s) disconnected: %s"
                .formatted(player.getName(), player.getUniqueId(), details));
    }

    /**
     * Logs attempted kicks without treating them as disconnects because the event may be cancelled.
     *
     * @param event
     *     Bukkit kick event.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    void onPlayerKick(PlayerKickEvent event)
    {
        final Player player = event.getPlayer();
        if (!playerStore.isSyntheticPlayer(player.getUniqueId()))
            return;
        logger.info(
            "LK_AGENT: Synthetic player '%s' (%s) kick event: cancelled=%s reason=%s"
                .formatted(player.getName(), player.getUniqueId(), event.isCancelled(), event.getReason()));
    }

    private static String formatLocation(Location location)
    {
        final World world = location.getWorld();
        final String worldName = world == null ? "<no-world>" : world.getName();
        return String.format(
            Locale.ROOT,
            "%s[%.2f, %.2f, %.2f]",
            worldName,
            location.getX(),
            location.getY(),
            location.getZ()
        );
    }

    private static String formatDamageSource(@Nullable DamageSource damageSource)
    {
        return damageSource == null ? "<unknown>" : damageSource.getDamageType().getKey().toString();
    }
}
