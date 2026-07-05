package nl.pim16aap2.lightkeeper.agent.spigot;

import org.bukkit.Bukkit;

import java.util.Locale;

/**
 * Server platform detection utility.
 *
 * <p>Identifies whether the running server is Paper, Spigot/CraftBukkit, or an unknown platform
 * using a combination of heuristic signals: Paper-specific class presence, Bukkit name, Bukkit
 * version string, and the server implementation class name.
 *
 * <p>Example usage in an end-to-end test:
 * <pre>{@code
 *     String platform = framework.platform().name(); // "PAPER" | "SPIGOT" | "UNKNOWN"
 * }</pre>
 */
final class AgentPlatformDetector
{
    private AgentPlatformDetector()
    {
    }

    /**
     * Detects the server platform from live Bukkit globals.
     *
     * @return
     *     {@code "PAPER"}, {@code "SPIGOT"}, or {@code "UNKNOWN"}.
     */
    static String detect()
    {
        final ClassLoader serverClassLoader = Bukkit.getServer().getClass().getClassLoader();
        return detect(
            Bukkit.getName(),
            Bukkit.getVersion(),
            Bukkit.getServer().getClass().getName(),
            isClassPresent("io.papermc.paper.ServerBuildInfo", serverClassLoader)
        );
    }

    /**
     * Classifies the server platform from its identifying attributes.
     *
     * <p>Paper is preferred over Spigot when the Paper build-info class is present on the server
     * class loader, or when either the name or version string contains "paper". This is exposed as
     * a package-private overload for unit testing without a live Bukkit server.
     *
     * @param bukkitName
     *     Value of {@link org.bukkit.Bukkit#getName()}.
     * @param bukkitVersion
     *     Value of {@link org.bukkit.Bukkit#getVersion()}.
     * @param serverClassName
     *     Fully qualified class name of {@link org.bukkit.Bukkit#getServer()}.
     * @param paperClassPresent
     *     Whether {@code io.papermc.paper.ServerBuildInfo} is loadable from the server class loader.
     * @return
     *     {@code "PAPER"}, {@code "SPIGOT"}, or {@code "UNKNOWN"}.
     */
    static String detect(
        String bukkitName,
        String bukkitVersion,
        String serverClassName,
        boolean paperClassPresent)
    {
        if (paperClassPresent || containsIgnoreCase(bukkitName, "paper") || containsIgnoreCase(bukkitVersion, "paper"))
            return "PAPER";
        if (containsIgnoreCase(bukkitName, "spigot") ||
            containsIgnoreCase(bukkitName, "craftbukkit") ||
            containsIgnoreCase(bukkitVersion, "spigot") ||
            containsIgnoreCase(serverClassName, "craftbukkit"))
        {
            return "SPIGOT";
        }
        return "UNKNOWN";
    }

    private static boolean isClassPresent(String className, ClassLoader classLoader)
    {
        try
        {
            Class.forName(className, false, classLoader);
            return true;
        }
        catch (ClassNotFoundException exception)
        {
            return false;
        }
    }

    private static boolean containsIgnoreCase(String value, String fragment)
    {
        return value.toLowerCase(Locale.ROOT).contains(fragment.toLowerCase(Locale.ROOT));
    }
}
