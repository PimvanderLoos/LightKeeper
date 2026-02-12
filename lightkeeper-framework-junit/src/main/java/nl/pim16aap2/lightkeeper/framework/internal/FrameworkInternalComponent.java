package nl.pim16aap2.lightkeeper.framework.internal;

import dagger.BindsInstance;
import dagger.Component;
import nl.pim16aap2.lightkeeper.runtime.RuntimeManifest;

import javax.inject.Singleton;

/**
 * Internal Dagger component wiring framework services.
 */
@Singleton
@Component
interface FrameworkInternalComponent
{
    DefaultLightkeeperFramework framework();

    @Component.Factory
    interface Factory
    {
        FrameworkInternalComponent create(
            @BindsInstance RuntimeManifest runtimeManifest,
            @BindsInstance PaperServerProcess paperServerProcess,
            @BindsInstance UdsAgentClient udsAgentClient
        );
    }
}
