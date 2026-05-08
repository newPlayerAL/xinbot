package xin.bbtt.mcbot.command;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandManagerTest {

    @Test
    void testTokenizeBasic() {
        String input = "say hello world";
        List<String> tokens = CommandManager.tokenize(input);
        
        assertThat(tokens).containsExactly("say", "hello", "world");
    }

    @Test
    void testTokenizeWithQuotes() {
        String input = "say \"hello world\"";
        List<String> tokens = CommandManager.tokenize(input);
        
        assertThat(tokens).containsExactly("say", "hello world");
    }

    @Test
    void testTokenizeWithSingleQuotes() {
        String input = "say 'hello world'";
        List<String> tokens = CommandManager.tokenize(input);
        
        assertThat(tokens).containsExactly("say", "hello world");
    }

    @Test
    void testTokenizeWithEscapedQuotes() {
        String input = "say \"hello \\\"world\\\"\"";
        List<String> tokens = CommandManager.tokenize(input);
        
        assertThat(tokens).containsExactly("say", "hello \"world\"");
    }

    @Test
    void testTokenizeWithMultipleSpaces() {
        String input = "say   hello    world";
        List<String> tokens = CommandManager.tokenize(input);
        
        assertThat(tokens).containsExactly("say", "hello", "world");
    }

    @Test
    void testTokenizeTrailingSpace() {
        String input = "say hello ";
        List<String> tokens = CommandManager.tokenize(input);
        
        assertThat(tokens).containsExactly("say", "hello", "");
    }
    
    @Test
    void testTokenizeEmpty() {
        String input = "";
        List<String> tokens = CommandManager.tokenize(input);
        
        assertThat(tokens).isEmpty();
    }
}
