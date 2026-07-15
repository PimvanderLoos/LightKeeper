package nl.pim16aap2.lightkeeper.maven.test;

import nl.pim16aap2.lightkeeper.framework.ILightkeeperFramework;
import nl.pim16aap2.lightkeeper.framework.LightkeeperExtension;
import nl.pim16aap2.lightkeeper.framework.WorldHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.nio.file.Path;

import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.assertThat;
import static nl.pim16aap2.lightkeeper.framework.assertions.LightkeeperAssertions.catchThrowable;

@ExtendWith(LightkeeperExtension.class)
class LightkeeperWorldTemplateIT
{
    private static final String TEMPLATE_NAME = "lightkeeper-fixture-world";

    @Test
    void newWorldFromTemplate_shouldLoadProvisionedTemplateWorld(ILightkeeperFramework framework)
    {
        // setup
        final Path templateDirectory = framework.server().directory().resolve(TEMPLATE_NAME);

        // execute
        final WorldHandle templateWorld = framework.worlds().fromTemplate(TEMPLATE_NAME);

        // verify
        assertThat(templateWorld).hasNonBlankName();
        assertThat(templateWorld.name()).isEqualTo(TEMPLATE_NAME);
        // The provisioned folder is the loaded world's directory: the fixture marker must still be in place.
        assertThat(templateDirectory.resolve("fixtures").resolve("marker.txt")).exists();
    }

    @Test
    void newWorldFromTemplate_shouldRejectUnknownTemplateName(ILightkeeperFramework framework)
    {
        // setup
        final String unknownTemplate = "lightkeeper-no-such-template";

        // execute
        final Throwable thrown = catchThrowable(() -> framework.worlds().fromTemplate(unknownTemplate));

        // verify
        assertThat(thrown)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(unknownTemplate)
            .hasMessageContaining(TEMPLATE_NAME);
    }
}
