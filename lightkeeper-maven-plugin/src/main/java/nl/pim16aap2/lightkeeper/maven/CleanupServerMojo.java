package nl.pim16aap2.lightkeeper.maven;

import nl.pim16aap2.lightkeeper.maven.util.FileUtil;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.jspecify.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Cleans target server directories after successful integration tests.
 */
@Mojo(name = "cleanup-server", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public final class CleanupServerMojo extends AbstractMojo
{
    @Parameter(property = "lightkeeper.deleteTargetServerOnSuccess", defaultValue = "false")
    private boolean deleteTargetServerOnSuccess;

    @Parameter(
        property = "lightkeeper.serverWorkDirectoryRoot",
        defaultValue = "${project.build.directory}/lightkeeper-server",
        required = true
    )
    private @Nullable Path serverWorkDirectoryRoot;

    @Parameter(
        property = "lightkeeper.failsafeSummaryPath",
        defaultValue = "${project.build.directory}/failsafe-reports/failsafe-summary.xml",
        required = true
    )
    private @Nullable Path failsafeSummaryPath;

    /**
     * Deletes the prepared target server directory when integration tests succeeded and cleanup is enabled.
     */
    @Override
    public void execute()
        throws MojoExecutionException
    {
        if (!deleteTargetServerOnSuccess)
        {
            getLog().info("LK_CLEANUP: Skipping cleanup because deleteTargetServerOnSuccess=false.");
            return;
        }

        final Path effectiveFailsafeSummaryPath =
            Objects.requireNonNull(failsafeSummaryPath, "failsafeSummaryPath may not be null.");
        final Path effectiveServerWorkDirectoryRoot =
            Objects.requireNonNull(serverWorkDirectoryRoot, "serverWorkDirectoryRoot may not be null.");

        if (Files.notExists(effectiveFailsafeSummaryPath))
        {
            getLog().warn(
                "LK_CLEANUP: Failsafe summary '%s' does not exist. Keeping target server directory."
                    .formatted(effectiveFailsafeSummaryPath)
            );
            return;
        }

        final FailsafeSummary summary = readFailsafeSummary(effectiveFailsafeSummaryPath);
        if (summary.failures() > 0 || summary.errors() > 0)
        {
            getLog().warn(
                "LK_CLEANUP: Not deleting target server directory because tests reported failures=%d errors=%d."
                    .formatted(summary.failures(), summary.errors())
            );
            return;
        }

        if (Files.notExists(effectiveServerWorkDirectoryRoot))
        {
            getLog().info(
                "LK_CLEANUP: Target server directory '%s' does not exist. Nothing to delete."
                    .formatted(effectiveServerWorkDirectoryRoot)
            );
            return;
        }

        getLog().info("LK_CLEANUP: Deleting target server directory '%s'.".formatted(effectiveServerWorkDirectoryRoot));
        FileUtil.deleteRecursively(effectiveServerWorkDirectoryRoot, "target server directory");
    }

    private static FailsafeSummary readFailsafeSummary(Path path)
        throws MojoExecutionException
    {
        try
        {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            final Document document = factory.newDocumentBuilder().parse(path.toFile());
            final Element root = document.getDocumentElement();
            final int failures = parseChildInt(root, "failures");
            final int errors = parseChildInt(root, "errors");
            return new FailsafeSummary(failures, errors);
        }
        catch (Exception exception)
        {
            throw new MojoExecutionException(
                "Failed to read failsafe summary from '%s'.".formatted(path),
                exception
            );
        }
    }

    private static int parseChildInt(Element root, String name)
    {
        final NodeList nodes = root.getElementsByTagName(name);
        if (nodes.getLength() == 0)
            return 0;
        final String value = nodes.item(0).getTextContent();
        return value == null || value.isBlank() ? 0 : Integer.parseInt(value.trim());
    }

    private record FailsafeSummary(int failures, int errors)
    {
    }
}
