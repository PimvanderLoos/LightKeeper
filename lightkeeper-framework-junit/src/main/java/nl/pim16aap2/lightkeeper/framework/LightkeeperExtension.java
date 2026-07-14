package nl.pim16aap2.lightkeeper.framework;

import nl.pim16aap2.lightkeeper.framework.internal.DefaultLightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.internal.FailureDiagnosticsWriter;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/**
 * JUnit extension for framework lifecycle management.
 *
 * <p>On test failure the extension writes a diagnostics bundle (test outcome, captured server errors, server
 * console output) before any cleanup runs — see {@link FailureDiagnosticsWriter}. Two system properties control
 * this: {@code lightkeeper.diagnostics} ({@code on-failure} (default) | {@code always} | {@code off}) and
 * {@code lightkeeper.diagnosticsDirectory} (default {@code target/lightkeeper-reports}, resolved against the
 * test JVM's working directory).
 */
public final class LightkeeperExtension implements
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    AfterAllCallback,
    ParameterResolver
{
    private static final System.Logger LOG = System.getLogger(LightkeeperExtension.class.getName());

    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(LightkeeperExtension.class);
    private static final String KEY_SHARED_FRAMEWORK = "shared-framework";
    private static final String KEY_METHOD_FRAMEWORK = "method-framework";
    private static final String KEY_CLASS_USES_FRESH_LIFECYCLE = "class-uses-fresh-lifecycle";
    private static final String KEY_CLASS_HAS_METHOD_FRESH_SERVERS = "class-has-method-fresh-servers";

    /**
     * Starts a shared framework when tests are not configured for per-method fresh servers.
     *
     * <p>Also validates the diagnostics configuration up front: a typo in {@code lightkeeper.diagnostics} must
     * fail loudly here, before any server is provisioned — not during cleanup.
     */
    @Override
    public void beforeAll(ExtensionContext context)
    {
        diagnosticsMode();
        if (usesFreshLifecycleForClass(context) || hasMethodLevelFreshServers(context))
            return;
        getClassStore(context).put(KEY_SHARED_FRAMEWORK, startFramework());
    }

    /**
     * Starts or scopes framework state before each test method.
     */
    @Override
    public void beforeEach(ExtensionContext context)
    {
        if (usesFreshLifecycleForMethod(context))
        {
            closeSharedFrameworkIfPresent(context);
            getMethodStore(context).put(KEY_METHOD_FRAMEWORK, startFramework());
            return;
        }

        final ILightkeeperFramework sharedFramework = getOrStartSharedFramework(context);
        if (sharedFramework instanceof DefaultLightkeeperFramework defaultLightkeeperFramework)
            defaultLightkeeperFramework.beginMethodScope(context.getUniqueId());
    }

    /**
     * Cleans up method-scoped framework resources after each test method.
     *
     * <p>Diagnostics are captured first: JUnit's {@code TestWatcher} hooks only run after all
     * {@code afterEach} callbacks, by which point the framework is closed (fresh mode) or its error capture
     * cleared (shared mode) — so this is the last moment the failing test's state is still observable.
     */
    @Override
    public void afterEach(ExtensionContext context)
    {
        maybeWriteDiagnostics(context);

        if (!usesFreshLifecycleForMethod(context))
        {
            final ILightkeeperFramework sharedFramework = getClassStore(context).get(
                KEY_SHARED_FRAMEWORK,
                ILightkeeperFramework.class
            );
            if (sharedFramework instanceof DefaultLightkeeperFramework defaultLightkeeperFramework)
                defaultLightkeeperFramework.endMethodScope(context.getUniqueId());
            return;
        }

        final ILightkeeperFramework framework = getMethodStore(context).remove(
            KEY_METHOD_FRAMEWORK,
            ILightkeeperFramework.class
        );
        if (framework != null)
            framework.close();
    }

    /**
     * Closes shared framework state after all tests in the class.
     */
    @Override
    public void afterAll(ExtensionContext context)
    {
        final ILightkeeperFramework framework = getClassStore(context).remove(
            KEY_SHARED_FRAMEWORK,
            ILightkeeperFramework.class
        );
        if (framework != null)
            framework.close();
    }

    /**
     * Supports injection of {@link ILightkeeperFramework} parameters.
     */
    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException
    {
        return parameterContext.getParameter().getType().equals(ILightkeeperFramework.class);
    }

    /**
     * Resolves an active framework instance for parameter injection.
     */
    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException
    {
        return getFramework(extensionContext);
    }

    private static boolean usesFreshLifecycleForClass(ExtensionContext context)
    {
        final ExtensionContext.Store store = getClassStore(context);
        final Boolean cachedDecision = store.get(KEY_CLASS_USES_FRESH_LIFECYCLE, Boolean.class);
        if (cachedDecision != null)
            return cachedDecision;

        final Class<?> testClass = context.getRequiredTestClass();
        final boolean usesFreshLifecycle = testClass.isAnnotationPresent(FreshServer.class);

        store.put(KEY_CLASS_USES_FRESH_LIFECYCLE, usesFreshLifecycle);
        return usesFreshLifecycle;
    }

    private static boolean hasMethodLevelFreshServers(ExtensionContext context)
    {
        final ExtensionContext.Store store = getClassStore(context);
        final Boolean cachedDecision = store.get(KEY_CLASS_HAS_METHOD_FRESH_SERVERS, Boolean.class);
        if (cachedDecision != null)
            return cachedDecision;

        final boolean hasMethodLevelFreshServers = Arrays.stream(context.getRequiredTestClass().getDeclaredMethods())
            .anyMatch(method -> method.isAnnotationPresent(FreshServer.class));

        store.put(KEY_CLASS_HAS_METHOD_FRESH_SERVERS, hasMethodLevelFreshServers);
        return hasMethodLevelFreshServers;
    }

    private static boolean usesFreshLifecycleForMethod(ExtensionContext context)
    {
        if (usesFreshLifecycleForClass(context))
            return true;
        return context.getTestMethod()
            .map(testMethod -> testMethod.isAnnotationPresent(FreshServer.class))
            .orElse(false);
    }

    private static ILightkeeperFramework getFramework(ExtensionContext context)
    {
        final ILightkeeperFramework methodFramework = getMethodStore(context).get(
            KEY_METHOD_FRAMEWORK,
            ILightkeeperFramework.class
        );
        if (methodFramework != null)
            return methodFramework;

        if (usesFreshLifecycleForMethod(context))
        {
            closeSharedFrameworkIfPresent(context);
            final ILightkeeperFramework startedFramework = startFramework();
            getMethodStore(context).put(KEY_METHOD_FRAMEWORK, startedFramework);
            return startedFramework;
        }

        return getOrStartSharedFramework(context);
    }

    private static ILightkeeperFramework getOrStartSharedFramework(ExtensionContext context)
    {
        final ExtensionContext.Store store = getClassStore(context);
        final ILightkeeperFramework sharedFramework = store.get(KEY_SHARED_FRAMEWORK, ILightkeeperFramework.class);
        if (sharedFramework != null)
            return sharedFramework;

        final ILightkeeperFramework startedFramework = startFramework();
        store.put(KEY_SHARED_FRAMEWORK, startedFramework);
        return startedFramework;
    }

    private static void closeSharedFrameworkIfPresent(ExtensionContext context)
    {
        final ILightkeeperFramework sharedFramework = getClassStore(context).remove(
            KEY_SHARED_FRAMEWORK,
            ILightkeeperFramework.class
        );
        if (sharedFramework != null)
            sharedFramework.close();
    }

    private static ExtensionContext.Store getMethodStore(ExtensionContext context)
    {
        return context.getStore(NAMESPACE);
    }

    private static ExtensionContext.Store getClassStore(ExtensionContext context)
    {
        if (context.getTestMethod().isEmpty())
            return context.getStore(NAMESPACE);
        return context.getParent()
            .map(parentContext -> parentContext.getStore(NAMESPACE))
            .orElseGet(() -> context.getStore(NAMESPACE));
    }

    private static ILightkeeperFramework startFramework()
    {
        final String runtimeManifestPath = System.getProperty("lightkeeper.runtimeManifestPath", "").trim();
        if (runtimeManifestPath.isBlank())
            throw new IllegalStateException("System property 'lightkeeper.runtimeManifestPath' is not set.");
        return Lightkeeper.start(Path.of(runtimeManifestPath));
    }

    /**
     * Writes a diagnostics bundle when the configured mode calls for one and a live framework exists for the
     * current test. Never starts a framework and never throws — this runs before the cleanup in
     * {@link #afterEach}, and a diagnostics problem (including a mode value changed to garbage mid-run) must
     * not prevent that cleanup from running.
     */
    private static void maybeWriteDiagnostics(ExtensionContext context)
    {
        try
        {
            final DiagnosticsMode mode = diagnosticsMode();
            if (mode == DiagnosticsMode.OFF)
                return;

            final @Nullable Throwable failure = context.getExecutionException().orElse(null);
            if (!shouldWriteBundle(mode, failure))
                return;

            final @Nullable ILightkeeperFramework framework = findExistingFramework(context);
            if (framework == null)
                return;

            FailureDiagnosticsWriter.write(
                framework,
                diagnosticsDirectory(),
                context.getRequiredTestClass().getSimpleName(),
                context.getRequiredTestMethod().getName(),
                failure
            );
        }
        catch (RuntimeException exception)
        {
            LOG.log(
                System.Logger.Level.WARNING,
                "LK_DIAGNOSTICS: Skipping the diagnostics bundle because of an unexpected error.",
                exception
            );
        }
    }

    /**
     * Decides whether a bundle should be written for the given mode and collected throwable.
     *
     * <p>An assumption abort ({@link org.opentest4j.TestAbortedException}) means the test was skipped, not
     * failed: it never triggers an {@code on-failure} bundle, though {@code always} mode still records it.
     */
    static boolean shouldWriteBundle(DiagnosticsMode mode, @Nullable Throwable failure)
    {
        if (mode == DiagnosticsMode.ALWAYS)
            return true;
        if (mode == DiagnosticsMode.OFF)
            return false;
        return failure != null && !(failure instanceof org.opentest4j.TestAbortedException);
    }

    /**
     * Finds the framework bound to the current test, if any, without ever starting one.
     */
    private static @Nullable ILightkeeperFramework findExistingFramework(ExtensionContext context)
    {
        final ILightkeeperFramework methodFramework = getMethodStore(context).get(
            KEY_METHOD_FRAMEWORK,
            ILightkeeperFramework.class
        );
        if (methodFramework != null)
            return methodFramework;
        return getClassStore(context).get(KEY_SHARED_FRAMEWORK, ILightkeeperFramework.class);
    }

    static DiagnosticsMode diagnosticsMode()
    {
        final String configuredMode = System.getProperty("lightkeeper.diagnostics", "on-failure").trim();
        return switch (configuredMode.toLowerCase(Locale.ROOT))
        {
            // An empty value (e.g. a bare -Dlightkeeper.diagnostics=) means "use the default".
            case "", "on-failure" -> DiagnosticsMode.ON_FAILURE;
            case "always" -> DiagnosticsMode.ALWAYS;
            case "off" -> DiagnosticsMode.OFF;
            default -> throw new IllegalStateException(
                ("Unknown value '%s' for system property 'lightkeeper.diagnostics'; expected 'on-failure', "
                    + "'always', or 'off'.").formatted(configuredMode));
        };
    }

    private static Path diagnosticsDirectory()
    {
        return Path.of(System.getProperty("lightkeeper.diagnosticsDirectory", "target/lightkeeper-reports"));
    }

    /**
     * When the extension captures a diagnostics bundle.
     */
    enum DiagnosticsMode
    {
        /**
         * Never write bundles.
         */
        OFF,

        /**
         * Write a bundle only for failed tests (default).
         */
        ON_FAILURE,

        /**
         * Write a bundle for every test, passed or failed.
         */
        ALWAYS,
    }
}
