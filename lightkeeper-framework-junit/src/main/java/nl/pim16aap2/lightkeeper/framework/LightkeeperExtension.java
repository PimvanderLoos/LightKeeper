package nl.pim16aap2.lightkeeper.framework;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import java.nio.file.Path;

/**
 * JUnit extension for framework lifecycle management.
 */
public final class LightkeeperExtension implements
    BeforeAllCallback,
    BeforeEachCallback,
    AfterEachCallback,
    AfterAllCallback,
    ParameterResolver
{
    private static final ExtensionContext.Namespace NAMESPACE =
        ExtensionContext.Namespace.create(LightkeeperExtension.class);
    private static final String KEY_SHARED_FRAMEWORK = "shared-framework";
    private static final String KEY_METHOD_FRAMEWORK = "method-framework";

    @Override
    public void beforeAll(ExtensionContext context)
    {
        if (isFreshServer(context))
            return;
        context.getStore(NAMESPACE).put(KEY_SHARED_FRAMEWORK, startFramework());
    }

    @Override
    public void beforeEach(ExtensionContext context)
    {
        if (isFreshServer(context))
            context.getStore(NAMESPACE).put(KEY_METHOD_FRAMEWORK, startFramework());
    }

    @Override
    public void afterEach(ExtensionContext context)
    {
        if (!isFreshServer(context))
            return;

        final LightkeeperFramework framework = context.getStore(NAMESPACE).remove(
            KEY_METHOD_FRAMEWORK,
            LightkeeperFramework.class
        );
        if (framework != null)
            framework.close();
    }

    @Override
    public void afterAll(ExtensionContext context)
    {
        final LightkeeperFramework framework = context.getStore(NAMESPACE).remove(
            KEY_SHARED_FRAMEWORK,
            LightkeeperFramework.class
        );
        if (framework != null)
            framework.close();
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException
    {
        return parameterContext.getParameter().getType().equals(LightkeeperFramework.class);
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext)
        throws ParameterResolutionException
    {
        return getFramework(extensionContext);
    }

    private static boolean isFreshServer(ExtensionContext context)
    {
        final boolean classLevel = context.getRequiredTestClass().isAnnotationPresent(FreshServer.class);
        final boolean methodLevel = context.getTestMethod()
            .map(method -> method.isAnnotationPresent(FreshServer.class))
            .orElse(false);
        return classLevel || methodLevel;
    }

    private static LightkeeperFramework getFramework(ExtensionContext context)
    {
        final LightkeeperFramework methodFramework = context.getStore(NAMESPACE).get(
            KEY_METHOD_FRAMEWORK,
            LightkeeperFramework.class
        );
        if (methodFramework != null)
            return methodFramework;

        final LightkeeperFramework sharedFramework = context.getStore(NAMESPACE).get(
            KEY_SHARED_FRAMEWORK,
            LightkeeperFramework.class
        );
        if (sharedFramework != null)
            return sharedFramework;

        final LightkeeperFramework startedFramework = startFramework();
        context.getStore(NAMESPACE).put(KEY_METHOD_FRAMEWORK, startedFramework);
        return startedFramework;
    }

    private static LightkeeperFramework startFramework()
    {
        final String runtimeManifestPath = System.getProperty("lightkeeper.runtimeManifestPath", "").trim();
        if (runtimeManifestPath.isBlank())
            throw new IllegalStateException("System property 'lightkeeper.runtimeManifestPath' is not set.");
        return Lightkeeper.start(Path.of(runtimeManifestPath));
    }
}
