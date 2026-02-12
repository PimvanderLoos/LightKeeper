package nl.pim16aap2.lightkeeper.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

@Mojo(name = "test", defaultPhase = LifecyclePhase.INTEGRATION_TEST)
public class LightKeeperMojo extends AbstractMojo
{
    @Override
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        getLog().error("LightKeeperMojo!!");
    }
}
