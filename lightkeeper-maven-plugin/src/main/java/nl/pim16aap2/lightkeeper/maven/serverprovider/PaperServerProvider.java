package nl.pim16aap2.lightkeeper.maven.serverprovider;

import org.apache.maven.plugin.logging.Log;

public class PaperServerProvider extends ServerProvider
{
    private static final String SERVER_NAME = "Paper";

    public PaperServerProvider(Log log)
    {
        super(log, SERVER_NAME);
        throw new UnsupportedOperationException("Paper server provider is not yet implemented.");
    }
}
