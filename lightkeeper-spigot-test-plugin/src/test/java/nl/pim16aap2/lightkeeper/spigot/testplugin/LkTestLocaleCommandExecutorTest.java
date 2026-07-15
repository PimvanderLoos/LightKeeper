package nl.pim16aap2.lightkeeper.spigot.testplugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
import static org.mockito.Mockito.when;

class LkTestLocaleCommandExecutorTest
{
    @Test
    void onCommand_shouldLogPlayerNameAndLocaleForPlayerSender()
    {
        // setup
        final AtomicReference<LogRecord> captured = new AtomicReference<>();
        final Logger logger = loggerWithHandler(captured);
        final LkTestLocaleCommandExecutor executor = new LkTestLocaleCommandExecutor(logger);
        final Player player = mock();
        when(player.getName()).thenReturn("lklocale");
        when(player.getLocale()).thenReturn("de_de");
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(player, command, "lktestlocale", new String[0]);

        // verify
        assertThat(handled).isTrue();
        final LogRecord record = Objects.requireNonNull(captured.get(), "record");
        assertThat(record.getLevel()).isEqualTo(Level.INFO);
        assertThat(record.getMessage()).isEqualTo("LK_LOCALE name=lklocale locale=de_de");
    }

    @Test
    void onCommand_shouldSendPlayersOnlyMessageForNonPlayerSender()
    {
        // setup
        final AtomicReference<LogRecord> captured = new AtomicReference<>();
        final Logger logger = loggerWithHandler(captured);
        final LkTestLocaleCommandExecutor executor = new LkTestLocaleCommandExecutor(logger);
        final CommandSender sender = mock();
        final Command command = mock();

        // execute
        final boolean handled = executor.onCommand(sender, command, "lktestlocale", new String[0]);

        // verify
        assertThat(handled).isTrue();
        verify(sender).sendMessage(LkTestLocaleCommandExecutor.PLAYERS_ONLY_MESSAGE);
        assertThat(captured.get()).isNull();
    }

    private static Logger loggerWithHandler(AtomicReference<LogRecord> captured)
    {
        final Logger logger =
            Logger.getLogger(LkTestLocaleCommandExecutorTest.class.getName() + "." + captured.hashCode());
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
