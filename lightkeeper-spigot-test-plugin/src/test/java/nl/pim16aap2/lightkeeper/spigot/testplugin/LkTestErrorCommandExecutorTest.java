package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class LkTestErrorCommandExecutorTest
{
    @Test
    void onCommand_shouldLogSevereWithThrowableWhenModeIsSevere()
    {
        // setup
        final AtomicReference<LogRecord> captured = new AtomicReference<>();
        final Logger logger = loggerWithHandler(captured);
        final LkTestErrorCommandExecutor executor = new LkTestErrorCommandExecutor(logger);
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(sender, command, "lktesterror", new String[]{"severe"});

        // verify
        assertThat(handled).isTrue();
        final LogRecord record = Objects.requireNonNull(captured.get(), "record");
        assertThat(record.getLevel()).isEqualTo(Level.SEVERE);
        assertThat(record.getMessage()).isEqualTo(LkTestErrorCommandExecutor.SEVERE_MESSAGE);
        assertThat(record.getThrown())
            .isInstanceOf(IllegalStateException.class)
            .hasMessage(LkTestErrorCommandExecutor.THROWABLE_MESSAGE);
        verifyNoInteractions(sender);
    }

    @Test
    void onCommand_shouldLogWarningWithoutThrowableWhenModeIsWarning()
    {
        // setup
        final AtomicReference<LogRecord> captured = new AtomicReference<>();
        final Logger logger = loggerWithHandler(captured);
        final LkTestErrorCommandExecutor executor = new LkTestErrorCommandExecutor(logger);
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(sender, command, "lktesterror", new String[]{"warning"});

        // verify
        assertThat(handled).isTrue();
        final LogRecord record = Objects.requireNonNull(captured.get(), "record");
        assertThat(record.getLevel()).isEqualTo(Level.WARNING);
        assertThat(record.getMessage()).isEqualTo(LkTestErrorCommandExecutor.WARNING_MESSAGE);
        assertThat(record.getThrown()).isNull();
        verifyNoInteractions(sender);
    }

    @Test
    void onCommand_shouldBeCaseInsensitiveForMode()
    {
        // setup
        final AtomicReference<LogRecord> captured = new AtomicReference<>();
        final Logger logger = loggerWithHandler(captured);
        final LkTestErrorCommandExecutor executor = new LkTestErrorCommandExecutor(logger);
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        executor.onCommand(sender, command, "lktesterror", new String[]{"SEVERE"});

        // verify
        assertThat(Objects.requireNonNull(captured.get(), "record").getLevel()).isEqualTo(Level.SEVERE);
    }

    @Test
    void onCommand_shouldSendUsageMessageWhenArgsAreEmpty()
    {
        // setup
        final Logger logger = Logger.getLogger(LkTestErrorCommandExecutorTest.class.getName());
        final LkTestErrorCommandExecutor executor = new LkTestErrorCommandExecutor(logger);
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(sender, command, "lktesterror", new String[0]);

        // verify
        assertThat(handled).isTrue();
        verify(sender).sendMessage(LkTestErrorCommandExecutor.USAGE_MESSAGE);
    }

    @Test
    void onCommand_shouldSendUsageMessageWhenModeIsUnrecognized()
    {
        // setup
        final Logger logger = Logger.getLogger(LkTestErrorCommandExecutorTest.class.getName());
        final LkTestErrorCommandExecutor executor = new LkTestErrorCommandExecutor(logger);
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(sender, command, "lktesterror", new String[]{"bogus"});

        // verify
        assertThat(handled).isTrue();
        verify(sender).sendMessage(LkTestErrorCommandExecutor.USAGE_MESSAGE);
    }

    private static Logger loggerWithHandler(AtomicReference<LogRecord> captured)
    {
        final Logger logger = Logger.getLogger(LkTestErrorCommandExecutorTest.class.getName() + "." + captured.hashCode());
        logger.setUseParentHandlers(false);
        logger.setLevel(Level.ALL);
        logger.addHandler(new Handler()
        {
            @Override
            public void publish(LogRecord record)
            {
                captured.set(record);
            }

            @Override
            public void flush()
            {
            }

            @Override
            public void close()
            {
            }
        });
        return logger;
    }
}
