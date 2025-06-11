package nl.pim16aap2.lightkeeper.maven.serverprovider;

import nl.pim16aap2.lightkeeper.maven.ServerSpecification;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

public class PaperServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "paper";

    public PaperServerProvider(Log log, ServerSpecification serverSpecification)
    {
        super(log, SERVER_NAME, serverSpecification);
        throw new UnsupportedOperationException("Paper server provider is not yet implemented.");
    }

    @Override
    protected void createBaseServerJar()
        throws MojoExecutionException
    {
    }

    @Override
    protected void createBaseServer()
        throws MojoExecutionException
    {
    }
}
