package xin.bbtt.mcbot.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.bbtt.mcbot.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class EventManagerTest {

    private EventManager eventManager;
    private Plugin mockPlugin;

    @BeforeEach
    void setUp() {
        eventManager = new EventManager();
        mockPlugin = mock(Plugin.class);
    }

    public static class TestEvent extends Event {
        private static final HandlerList handlers = new HandlerList();
        
        public static HandlerList getHandlerList() {
            return handlers;
        }
        
        @Override
        public HandlerList getHandlers() {
            return handlers;
        }
    }

    public static class TestListener implements Listener {
        public List<String> callOrder = new ArrayList<>();

        @EventHandler(priority = EventPriority.NORMAL)
        public void onNormal(TestEvent event) {
            callOrder.add("NORMAL");
        }

        @EventHandler(priority = EventPriority.LOWEST)
        public void onLowest(TestEvent event) {
            callOrder.add("LOWEST");
        }

        @EventHandler(priority = EventPriority.MONITOR)
        public void onMonitor(TestEvent event) {
            callOrder.add("MONITOR");
        }
        
        @EventHandler(priority = EventPriority.HIGHEST)
        public void onHighest(TestEvent event) {
            callOrder.add("HIGHEST");
        }
    }

    @Test
    void testEventPriorityOrder() {
        TestListener listener = new TestListener();
        eventManager.registerEvents(listener, mockPlugin);

        eventManager.callEvent(new TestEvent());

        // Order should be LOWEST -> LOW -> NORMAL -> HIGH -> HIGHEST -> MONITOR
        assertThat(listener.callOrder).containsExactly("LOWEST", "NORMAL", "HIGHEST", "MONITOR");
    }

    @Test
    void testUnregisterAll() {
        TestListener listener = new TestListener();
        eventManager.registerEvents(listener, mockPlugin);

        eventManager.unregisterAll(mockPlugin);

        eventManager.callEvent(new TestEvent());

        // Since it was unregistered, it should not have been called
        assertThat(listener.callOrder).isEmpty();
    }
}
