package nl.pim16aap2.lightkeeper.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class GeneratedMetadataTest
{
    @Test
    void write_shouldReplaceACompleteDocumentAndRemoveTemporaryFiles(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path metadata = root.resolve("private/plan.json");
        GeneratedMetadata.write(new Value("old"), metadata);

        // execute
        GeneratedMetadata.write(new Value("current"), metadata);
        final Value value = GeneratedMetadata.read(metadata, Value.class);

        // verify
        assertThat(value.name()).isEqualTo("current");
        try (var files = Files.list(metadata.getParent()))
        {
            assertThat(files.map(path -> path.getFileName().toString())).containsExactly("plan.json");
        }
    }

    @Test
    void read_shouldRejectSymbolicLinks(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path original = Files.writeString(root.resolve("original.json"), "{}");
        final Path link = Files.createSymbolicLink(root.resolve("linked.json"), original);

        // execute
        final var failure = catchThrowable(() -> GeneratedMetadata.read(link, Value.class));

        // verify
        assertThat(failure).isInstanceOf(IOException.class).hasMessageContaining("not a regular file");
    }

    @Test
    void read_shouldBoundTheBytesActuallyRead(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path metadata = Files.writeString(root.resolve("oversized.json"), "x".repeat(1_048_577));

        // execute
        final var failure = catchThrowable(() -> GeneratedMetadata.read(metadata, Value.class));

        // verify
        assertThat(failure).isInstanceOf(IOException.class).hasMessageContaining("exceeds");
    }

    @Test
    void read_shouldRejectNullOrMalformedDocuments(@TempDir Path root)
        throws Exception
    {
        // setup
        final Path nullDocument = Files.writeString(root.resolve("null.json"), "null");
        final Path malformedDocument = Files.writeString(root.resolve("malformed.json"), "{");

        // execute
        final var nullFailure = catchThrowable(() -> GeneratedMetadata.read(nullDocument, Value.class));
        final var malformedFailure = catchThrowable(() -> GeneratedMetadata.read(malformedDocument, Value.class));

        // verify
        assertThat(nullFailure).isInstanceOf(IOException.class).hasMessageContaining("may not be null");
        assertThat(malformedFailure).isInstanceOf(IOException.class).hasMessageContaining("Failed to parse");
    }

    public record Value(String name)
    {
    }
}
