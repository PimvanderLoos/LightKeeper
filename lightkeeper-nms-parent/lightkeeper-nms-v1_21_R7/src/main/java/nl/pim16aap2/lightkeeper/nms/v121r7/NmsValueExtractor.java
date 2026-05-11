package nl.pim16aap2.lightkeeper.nms.v121r7;

import org.jspecify.annotations.Nullable;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Generic reflective value extractor shared by both text and chat-component extraction paths.
 *
 * <p>Both {@code extractText} and {@code extractComponentJson} in
 * {@link BotPlayerNmsAdapterV1_21_R7} follow the same recursive structure:
 * <ol>
 *   <li>Guard against null / exceeded depth / cycles.</li>
 *   <li>Detect leaf values and extract them with a caller-supplied function.</li>
 *   <li>Recurse into {@link Optional}, {@link Collection}, and array containers.</li>
 *   <li>Reflect over safe zero-arg accessors (capped at {@link #MAX_METHODS}) and recurse.</li>
 * </ol>
 *
 * <p>Callers supply the two points of variance:
 * <ul>
 *   <li>{@code leafExtractor} — detects leaf objects and converts them to a string; returns
 *       {@code null} if the value is not a leaf or extraction fails.</li>
 *   <li>{@code isSafeAccessor} — determines which zero-arg methods are safe to invoke reflectively.</li>
 * </ul>
 *
 * <p>The {@code skipBlank} flag controls whether blank-but-non-null results from recursive calls
 * are treated as absent (text extraction) or valid (component JSON extraction).
 *
 * <p><b>Design note on heuristic semantics:</b> both paths find the <em>first</em> matching leaf,
 * not the definitive chat content. Non-chat packets that expose title- or message-like getters may
 * produce false matches. This is a known trade-off of the reflection-based approach.
 */
final class NmsValueExtractor
{
    private static final System.Logger LOG = System.getLogger(NmsValueExtractor.class.getName());

    /**
     * Maximum number of accessor methods inspected per object instance to bound reflection overhead.
     */
    static final int MAX_METHODS = 24;

    /**
     * Maximum recursion depth; limits how deep the extractor follows object graphs.
     */
    static final int MAX_DEPTH = 4;

    private NmsValueExtractor()
    {
    }

    /**
     * Recursively extracts a string value from {@code value} using the supplied strategy.
     *
     * @param value
     *     Root object to inspect.
     * @param depth
     *     Remaining recursion budget.
     * @param seen
     *     Cycle-detection set keyed by object identity.
     * @param leafExtractor
     *     Returns a non-null string when {@code value} is a leaf; {@code null} otherwise.
     * @param isSafeAccessor
     *     Determines which zero-arg methods are safe to invoke reflectively.
     * @param skipBlank
     *     When {@code true}, blank (but non-null) results from recursive calls are skipped;
     *     use {@code false} for JSON where an empty string is a valid result.
     * @return
     *     Extracted string, or {@code null} when nothing was found.
     */
    static @Nullable String extract(
        @Nullable Object value,
        int depth,
        IdentityHashMap<Object, Boolean> seen,
        Function<Object, @Nullable String> leafExtractor,
        Predicate<Method> isSafeAccessor,
        boolean skipBlank)
    {
        if (value == null || depth < 0 || seen.put(value, Boolean.TRUE) != null)
            return null;

        final String leaf = leafExtractor.apply(value);
        if (leaf != null)
            return (!skipBlank || !leaf.isBlank()) ? leaf : null;

        if (value instanceof Optional<?> optional)
        {
            return optional.map(inner -> extract(inner, depth - 1, seen, leafExtractor, isSafeAccessor, skipBlank))
                .orElse(null);
        }
        if (value instanceof Collection<?> collection)
        {
            for (final Object element : collection)
            {
                final String result = extract(element, depth - 1, seen, leafExtractor, isSafeAccessor, skipBlank);
                if (result != null && (!skipBlank || !result.isBlank()))
                    return result;
            }
            return null;
        }
        if (value.getClass().isArray())
        {
            final int length = Array.getLength(value);
            for (int index = 0; index < length; ++index)
            {
                final String result =
                    extract(Array.get(value, index), depth - 1, seen, leafExtractor, isSafeAccessor, skipBlank);
                if (result != null && (!skipBlank || !result.isBlank()))
                    return result;
            }
            return null;
        }

        int inspectedMethodCount = 0;
        for (final Method method : value.getClass().getMethods())
        {
            if (!isSafeAccessor.test(method))
                continue;
            if (inspectedMethodCount >= MAX_METHODS)
                break;
            ++inspectedMethodCount;

            try
            {
                final Object nested = method.invoke(value);
                final String result = extract(nested, depth - 1, seen, leafExtractor, isSafeAccessor, skipBlank);
                if (result != null && (!skipBlank || !result.isBlank()))
                    return result;
            }
            catch (Exception exception)
            {
                LOG.log(System.Logger.Level.TRACE, "Ignoring reflective accessor failure during extraction.",
                    exception);
            }
        }
        return null;
    }
}
