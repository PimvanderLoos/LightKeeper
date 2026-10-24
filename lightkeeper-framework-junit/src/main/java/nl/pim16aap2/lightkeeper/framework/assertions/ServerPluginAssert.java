package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.IServerControl;
import nl.pim16aap2.lightkeeper.protocol.ServerPluginSnapshot;
import org.assertj.core.api.AbstractAssert;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Eager assertions for a named plugin, re-querying the server on every evaluation.
 *
 * <p>Both enabled-state assertions require the plugin to be present. Inherited null checks refer to the server
 * subject; use {@link #isPresent()} or {@link #isAbsent()} for plugin registration.
 */
public final class ServerPluginAssert extends AbstractAssert<ServerPluginAssert, @Nullable IServerControl>
{
    /**
     * Requested plugin name, passed to the server's canonical lookup and validation.
     */
    private final String pluginName;

    ServerPluginAssert(@Nullable IServerControl actual, String pluginName)
    {
        super(actual, ServerPluginAssert.class);
        this.pluginName = Objects.requireNonNull(pluginName, "pluginName may not be null.");
        if (pluginName.isBlank())
            throw new IllegalArgumentException("pluginName may not be blank.");
    }

    /**
     * Asserts that the plugin is registered, whether enabled or disabled.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerPluginAssert isPresent()
    {
        pluginSnapshot("be present");
        return this;
    }

    /**
     * Asserts that the plugin is not registered at this observation.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerPluginAssert isAbsent()
    {
        final var plugin = nonNullActual().plugin(pluginName);
        if (plugin.isPresent())
            failWithMessage("Expected plugin '%s' to be absent, but it was present.", pluginName);
        return this;
    }

    /**
     * Asserts that the plugin is present and enabled.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerPluginAssert isEnabled()
    {
        return hasEnabledState(true);
    }

    /**
     * Asserts that the plugin is present and disabled. A missing plugin fails this assertion.
     *
     * @return This assertion for fluent chaining.
     */
    public ServerPluginAssert isDisabled()
    {
        return hasEnabledState(false);
    }

    /**
     * Asserts that the plugin is present with exactly the requested version string.
     *
     * @param expectedVersion
     *     Expected version; compared without semantic-version interpretation.
     * @return This assertion for fluent chaining.
     */
    public ServerPluginAssert hasVersion(String expectedVersion)
    {
        Objects.requireNonNull(expectedVersion, "expectedVersion may not be null.");
        final ServerPluginSnapshot plugin = pluginSnapshot("have version '%s'".formatted(expectedVersion));
        if (!expectedVersion.equals(plugin.version()))
        {
            failWithMessage("Expected plugin '%s' to have version '%s', but version was '%s'.",
                pluginName, expectedVersion, plugin.version());
        }
        return this;
    }

    private ServerPluginAssert hasEnabledState(boolean expected)
    {
        final String expectedState = expected ? "enabled" : "disabled";
        final ServerPluginSnapshot plugin = pluginSnapshot("be " + expectedState);
        if (plugin.isEnabled() != expected)
        {
            failWithMessage("Expected plugin '%s' to be %s, but it was %s.",
                pluginName, expectedState, plugin.isEnabled() ? "enabled" : "disabled");
        }
        return this;
    }

    private ServerPluginSnapshot pluginSnapshot(String expectation)
    {
        final var plugin = nonNullActual().plugin(pluginName);
        if (plugin.isEmpty())
            failWithMessage("Expected plugin '%s' to %s, but it was not present.", pluginName, expectation);
        return plugin.orElseThrow();
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // isNotNull() establishes the non-null subject.
    private IServerControl nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
