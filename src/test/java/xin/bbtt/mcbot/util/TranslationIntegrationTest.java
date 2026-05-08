package xin.bbtt.mcbot.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xin.bbtt.mcbot.LangManager;
import xin.bbtt.mcbot.Utils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class TranslationIntegrationTest {

    @BeforeEach
    void setUp() {
        LangManager.clear();
    }

    @Test
    void testTranslatableComponentToString() {
        // Add a translation template
        LangManager.addTranslations(Map.of("chat.type.text", "<%s> %s"));
        
        // Create a translatable component: <Alice> Hello World
        Component component = Component.translatable("chat.type.text", 
                Component.text("Alice"), 
                Component.text("Hello World"));
        
        String result = Utils.toString(component);
        
        // Adventure components might add default colors if not careful, 
        // but Utils.toString only adds codes if present.
        // chat.type.text -> <Alice> Hello World
        assertThat(result).isEqualTo("<Alice> Hello World");
    }

    @Test
    void testColorPreservationInTranslation() {
        LangManager.addTranslations(Map.of("test.color", "Colored: %s"));
        
        // Create a translatable component with colored argument
        Component component = Component.translatable("test.color", 
                Component.text("Red").color(NamedTextColor.RED));
        
        String result = Utils.toString(component);
        
        // §c is the code for RED
        assertThat(result).isEqualTo("Colored: §cRed");
    }
}
