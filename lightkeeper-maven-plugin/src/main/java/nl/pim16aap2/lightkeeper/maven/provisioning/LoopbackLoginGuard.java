package nl.pim16aap2.lightkeeper.maven.provisioning;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fail-loud guard against server configurations that would break {@code FULL_LOGIN} loopback logins.
 *
 * <p>LightKeeper's full-login bots complete a real offline-mode login over loopback TCP against the provisioned
 * server. Three configuration switches silently break that pipeline for every bot: {@code online-mode=true}
 * ({@code server.properties}) makes the server demand Mojang session authentication, and
 * {@code settings.bungeecord: true} ({@code spigot.yml}) or {@code proxies.velocity.enabled: true}
 * ({@code config/paper-global.yml}) make the server expect proxy-forwarded handshakes that a direct loopback
 * client never sends. LightKeeper's own provisioning never writes any of these, but a user config overlay could
 * flip them, so this guard validates the fully materialized target server directory — after all overlays are
 * applied — and fails the build with an actionable message instead of letting every full-login join time out.
 *
 * <p>The checks are deliberate line-level scans, not a YAML parse: the goal is failing loudly on the known-bad
 * switches in the standard configuration shapes those files ship with, with zero new dependencies. Commented-out
 * lines are ignored; files that do not exist are skipped.
 */
public final class LoopbackLoginGuard
{
    /**
     * Matches an active {@code bungeecord: true} entry in {@code spigot.yml} (the key only exists under the
     * top-level {@code settings} section in stock Spigot configurations), with an optional trailing comment.
     */
    private static final Pattern SPIGOT_BUNGEECORD_ENABLED_PATTERN =
        Pattern.compile("^\\s*bungeecord:\\s*true\\s*(?:#.*)?$");

    /**
     * Matches the opening of the {@code velocity} section in {@code config/paper-global.yml}, capturing its
     * indentation so the guard can tell when the section ends.
     */
    private static final Pattern VELOCITY_SECTION_PATTERN = Pattern.compile("^(\\s*)velocity:\\s*(?:#.*)?$");

    /**
     * Matches an active {@code enabled: true} entry (inside the {@code velocity} section), with an optional
     * trailing comment.
     */
    private static final Pattern VELOCITY_ENABLED_PATTERN = Pattern.compile("^\\s*enabled:\\s*true\\s*(?:#.*)?$");

    private LoopbackLoginGuard()
    {
    }

    /**
     * Validates a fully materialized target server directory for {@code FULL_LOGIN} compatibility.
     *
     * <p>Must run after every provisioning mutation (base-server copy, plugin installs, config overlay), because
     * the overlay is the mechanism through which the forbidden settings could enter.
     *
     * @param targetServerDirectory
     *     The materialized server directory to validate.
     * @param log
     *     Maven log sink for the pass confirmation.
     * @throws MojoExecutionException
     *     If a configuration file enables online mode or proxy forwarding, or a present file cannot be read.
     */
    public static void validate(Path targetServerDirectory, Log log)
        throws MojoExecutionException
    {
        validateServerProperties(targetServerDirectory.resolve("server.properties"));
        validateSpigotConfiguration(targetServerDirectory.resolve("spigot.yml"));
        validatePaperGlobalConfiguration(targetServerDirectory.resolve("config").resolve("paper-global.yml"));
        log.info("LK_GUARD: Loopback-login guard passed: offline mode, no proxy forwarding.");
    }

    private static void validateServerProperties(Path serverPropertiesFile)
        throws MojoExecutionException
    {
        for (final String line : readLinesIfPresent(serverPropertiesFile))
        {
            final String trimmed = line.trim();
            if (trimmed.startsWith("#") || !trimmed.startsWith("online-mode="))
                continue;

            final String value = trimmed.substring("online-mode=".length()).trim().toLowerCase(Locale.ROOT);
            if ("true".equals(value))
            {
                throw new MojoExecutionException(
                    ("Provisioned server configuration '%s' sets 'online-mode=true'. FULL_LOGIN bots complete a "
                        + "real offline-mode login over loopback TCP; online mode makes the server demand Mojang "
                        + "session authentication and breaks every full-login join. Remove the override "
                        + "(LightKeeper provisions 'online-mode=false').")
                        .formatted(serverPropertiesFile)
                );
            }
        }
    }

    private static void validateSpigotConfiguration(Path spigotConfigurationFile)
        throws MojoExecutionException
    {
        for (final String line : readLinesIfPresent(spigotConfigurationFile))
        {
            if (SPIGOT_BUNGEECORD_ENABLED_PATTERN.matcher(line).matches())
            {
                throw new MojoExecutionException(
                    ("Provisioned server configuration '%s' enables BungeeCord proxy forwarding "
                        + "('bungeecord: true'). The server would expect proxy-forwarded handshakes, which breaks "
                        + "FULL_LOGIN loopback logins. Remove the setting from the config overlay.")
                        .formatted(spigotConfigurationFile)
                );
            }
        }
    }

    private static void validatePaperGlobalConfiguration(Path paperGlobalConfigurationFile)
        throws MojoExecutionException
    {
        int velocitySectionIndent = -1;
        for (final String line : readLinesIfPresent(paperGlobalConfigurationFile))
        {
            final String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#"))
                continue;

            final int indent = line.length() - line.stripLeading().length();
            if (velocitySectionIndent >= 0 && indent <= velocitySectionIndent)
                velocitySectionIndent = -1;

            final Matcher velocitySectionMatcher = VELOCITY_SECTION_PATTERN.matcher(line);
            if (velocitySectionMatcher.matches())
            {
                velocitySectionIndent = velocitySectionMatcher.group(1).length();
                continue;
            }

            if (velocitySectionIndent >= 0 && VELOCITY_ENABLED_PATTERN.matcher(line).matches())
            {
                throw new MojoExecutionException(
                    ("Provisioned server configuration '%s' enables Velocity proxy forwarding "
                        + "('proxies.velocity.enabled: true'). The server would expect proxy-forwarded handshakes, "
                        + "which breaks FULL_LOGIN loopback logins. Remove the setting from the config overlay.")
                        .formatted(paperGlobalConfigurationFile)
                );
            }
        }
    }

    private static List<String> readLinesIfPresent(Path configurationFile)
        throws MojoExecutionException
    {
        if (Files.notExists(configurationFile))
            return List.of();

        try
        {
            return Files.readAllLines(configurationFile, StandardCharsets.UTF_8);
        }
        catch (IOException exception)
        {
            throw new MojoExecutionException(
                "Failed to read provisioned server configuration '%s' for the loopback-login guard."
                    .formatted(configurationFile),
                exception
            );
        }
    }
}
