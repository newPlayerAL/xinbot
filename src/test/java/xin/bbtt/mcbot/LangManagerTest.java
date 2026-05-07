package xin.bbtt.mcbot;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class LangManagerTest {

    @BeforeEach
    void setUp() {
        LangManager.clear();
    }

    @Test
    void testBasicGetAndAdd() {
        LangManager.addTranslations(Map.of("test.key", "Hello World"));
        assertThat(LangManager.get("test.key")).isEqualTo("Hello World");
    }

    @Test
    void testGetFallbackToKey() {
        assertThat(LangManager.get("non.existent.key")).isEqualTo("non.existent.key");
    }

    @Test
    void testFormatGet() {
        LangManager.addTranslations(Map.of("test.format", "Hello %s, you have %d messages."));
        assertThat(LangManager.get("test.format", "Alice", 5)).isEqualTo("Hello Alice, you have 5 messages.");
    }

    @Test
    void testFormatGetFallbackOnError() {
        LangManager.addTranslations(Map.of("test.format.error", "Hello %d"));
        // Invalid arguments for %d
        assertThat(LangManager.get("test.format.error", "Alice")).isEqualTo("Hello %d");
    }
}
