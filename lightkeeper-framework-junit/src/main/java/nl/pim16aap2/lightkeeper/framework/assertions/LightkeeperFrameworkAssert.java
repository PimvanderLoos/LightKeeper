package nl.pim16aap2.lightkeeper.framework.assertions;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;
import org.assertj.core.api.ListAssert;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Assertions for framework-level runtime state.
 */
public final class LightkeeperFrameworkAssert
    extends AbstractAssert<LightkeeperFrameworkAssert, @Nullable ILightkeeperFramework>
{
    LightkeeperFrameworkAssert(@Nullable ILightkeeperFramework actual)
    {
        super(actual, LightkeeperFrameworkAssert.class);
    }

    /**
     * Returns an AssertJ list assertion over captured server output.
     *
     * @return AssertJ list assertion for captured server output lines.
     */
    public ListAssert<String> serverOutput()
    {
        return Assertions.assertThat(nonNullActual().serverOutput());
    }

    /**
     * Asserts that captured server output does not contain common error or exception markers.
     *
     * @return This assertion for fluent chaining.
     */
    public LightkeeperFrameworkAssert hasNoServerErrors()
    {
        final List<String> errorLines = nonNullActual().serverOutput().stream()
            .filter(LightkeeperFrameworkAssert::isServerErrorLine)
            .toList();
        if (!errorLines.isEmpty())
        {
            failWithMessage(
                "Expected server output to contain no error lines, but found:%n%s",
                String.join(System.lineSeparator(), errorLines)
            );
        }
        return this;
    }

    private static boolean isServerErrorLine(String line)
    {
        final String normalized = line.toLowerCase(Locale.ROOT);
        return normalized.contains("severe")
            || normalized.contains("[error]")
            || normalized.contains("exception")
            || normalized.contains("caused by:");
    }

    @SuppressWarnings({"NullAway", "DataFlowIssue"}) // we call isNotNull() first, so actual is not null after that
    private ILightkeeperFramework nonNullActual()
    {
        isNotNull();
        return actual;
    }
}
