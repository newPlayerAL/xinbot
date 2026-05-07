package xin.bbtt.mcbot.command;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.bbtt.mcbot.plugin.Plugin;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class CommandIntegrationTest {

    private CommandManager commandManager;
    private Plugin mockPlugin;

    @BeforeEach
    void setUp() {
        commandManager = new CommandManager();
        mockPlugin = mock(Plugin.class);
    }

    public static class MockExecutor extends CommandExecutor {
        public AtomicReference<String[]> lastArgs = new AtomicReference<>();
        public AtomicReference<String> lastLabel = new AtomicReference<>();
        public CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onCommand(Command command, String label, String[] args) {
            lastLabel.set(label);
            lastArgs.set(args);
            latch.countDown();
        }
    }

    @Test
    void testCommandRegistrationAndExecution() throws InterruptedException {
        MockExecutor executor = new MockExecutor();
        Command command = new Command("test", new String[]{"t", "testalias"}, "A test command", "/test <args>");
        
        commandManager.registerCommand(command, executor, mockPlugin);
        
        // Execute main label
        commandManager.callCommand("test arg1 arg2");
        
        assertThat(executor.latch.await(2, TimeUnit.SECONDS)).isTrue();
        
        assertThat(executor.lastLabel.get()).isEqualTo("test");
        assertThat(executor.lastArgs.get()).containsExactly("arg1", "arg2");
        
        // Execute alias
        executor.lastLabel.set(null);
        executor.lastArgs.set(null);
        executor.latch = new CountDownLatch(1);
        commandManager.callCommand("t hello");
        
        assertThat(executor.latch.await(2, TimeUnit.SECONDS)).isTrue();
        
        assertThat(executor.lastLabel.get()).isEqualTo("t");
        assertThat(executor.lastArgs.get()).containsExactly("hello");
    }

    @Test
    void testCommandWithQuotes() throws InterruptedException {
        MockExecutor executor = new MockExecutor();
        Command command = new Command("mysay", new String[]{}, "Say something", "/mysay <message>");
        commandManager.registerCommand(command, executor, mockPlugin);
        
        commandManager.callCommand("mysay \"hello world\" 'single quoted'");
        
        assertThat(executor.latch.await(2, TimeUnit.SECONDS)).isTrue();
        
        assertThat(executor.lastArgs.get()).containsExactly("hello world", "single quoted");
    }
}
