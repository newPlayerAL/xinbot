package xin.bbtt.mcbot.LoginFlow;

import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class LoginFlowTest {

    private Session mockSession;
    private List<String> sentCommands;
    private LoginFlowBuilder baseBuilder;

    static class FakePacketA implements Packet {
        private final String data;
        FakePacketA(String data) { this.data = data; }
        String getData() { return data; }
    }

    static class FakePacketB implements Packet {
        private final String data;
        FakePacketB(String data) { this.data = data; }
        String getData() { return data; }
    }

    @BeforeEach
    void setUp() {
        mockSession = mock(Session.class);
        sentCommands = new ArrayList<>();
        baseBuilder = LoginFlow.builder(sentCommands::add);
    }

    @Nested
    class BuilderValidation {
        @Test
        void buildWithoutStepsThrows() {
            assertThatThrownBy(() -> baseBuilder.build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one step");
        }

        @Test
        void buildWithNullCommandSenderThrows() {
            assertThatThrownBy(() -> LoginFlow.builder(null))
                .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class SingleStep {
        @Test
        void matchAndSendCommand() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("login"))
                    .then("l mypassword")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("please login"));

            assertThat(sentCommands).containsExactly("l mypassword");
        }

        @Test
        void doesNotSendWhenPredicateFails() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("login"))
                    .then("l mypassword")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("something else"));

            assertThat(sentCommands).isEmpty();
        }

        @Test
        void stepWithoutCommandJustAdvances() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("ready"))
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("ready"));

            assertThat(sentCommands).isEmpty();
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);
            assertThat(flow.getCurrentStepIndex()).isEqualTo(1);
        }

        @Test
        void ignoresDifferentPacketType() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("trigger"))
                    .then("cmd")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketB("trigger"));

            assertThat(sentCommands).isEmpty();
            assertThat(flow.getCurrentStepIndex()).isEqualTo(0);
        }
    }

    @Nested
    class MultiStep {
        @Test
        void stepsExecuteInOrder() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("step1"))
                    .then("cmd1")
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("step2"))
                    .then("cmd2")
                .add()
                .cooldown(0)
                .build();

            assertThat(flow.getCurrentStepIndex()).isEqualTo(0);
            assertThat(flow.getTotalSteps()).isEqualTo(2);

            flow.packetReceived(mockSession, new FakePacketA("step1"));
            assertThat(sentCommands).containsExactly("cmd1");
            assertThat(flow.getCurrentStepIndex()).isEqualTo(1);
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.WAITING);

            flow.packetReceived(mockSession, new FakePacketA("step2"));
            assertThat(sentCommands).containsExactly("cmd1", "cmd2");
            assertThat(flow.getCurrentStepIndex()).isEqualTo(2);
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);
        }

        @Test
        void ignoresIrrelevantPacketsBetweenSteps() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("trigger"))
                    .then("cmd")
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("done"))
                    .then("final")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("trigger"));
            flow.packetReceived(mockSession, new FakePacketA("noise"));
            flow.packetReceived(mockSession, new FakePacketA("more noise"));
            flow.packetReceived(mockSession, new FakePacketA("done"));

            assertThat(sentCommands).containsExactly("cmd", "final");
        }

        @Test
        void differentPacketTypesInDifferentSteps() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("a"))
                    .then("cmdA")
                .step(FakePacketB.class)
                    .match(p -> p.getData().contains("b"))
                    .then("cmdB")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("a"));
            assertThat(sentCommands).containsExactly("cmdA");

            // FakePacketA for step 2 should be ignored (wrong type)
            flow.packetReceived(mockSession, new FakePacketA("b"));
            assertThat(sentCommands).containsExactly("cmdA");

            flow.packetReceived(mockSession, new FakePacketB("b"));
            assertThat(sentCommands).containsExactly("cmdA", "cmdB");
        }

        @Test
        void threeStepFlow() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("1"))
                    .then("cmd1")
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("2"))
                    .then("cmd2")
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("3"))
                    .then("cmd3")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("1"));
            flow.packetReceived(mockSession, new FakePacketA("2"));
            flow.packetReceived(mockSession, new FakePacketA("3"));

            assertThat(sentCommands).containsExactly("cmd1", "cmd2", "cmd3");
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);
        }
    }

    @Nested
    class Cooldown {
        @Test
        void cooldownPreventsRapidCommands() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                .add()
                .cooldown(10000)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));
            flow.packetReceived(mockSession, new FakePacketA("x"));
            flow.packetReceived(mockSession, new FakePacketA("x"));

            assertThat(sentCommands).hasSize(1);
        }

        @Test
        void cooldownOnlyAffectsCurrentStep() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("1"))
                    .then("cmd1")
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("2"))
                    .then("cmd2")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("1"));
            flow.packetReceived(mockSession, new FakePacketA("2"));

            assertThat(sentCommands).containsExactly("cmd1", "cmd2");
        }
    }

    @Nested
    class TemplateExpansion {
        @Test
        void templateExpanderModifiesCommand() {
            LoginFlow flow = baseBuilder
                .templateExpander(t -> t.replace("{password}", "secret123"))
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("l {password}")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));

            assertThat(sentCommands).containsExactly("l secret123");
        }

        @Test
        void multiplePlaceholders() {
            LoginFlow flow = baseBuilder
                .templateExpander(t -> t
                    .replace("{password}", "mypass")
                    .replace("{username}", "myuser"))
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("login {username} {password}")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));

            assertThat(sentCommands).containsExactly("login myuser mypass");
        }

        @Test
        void noExpanderUsesRawTemplate() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("l {password}")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));

            assertThat(sentCommands).containsExactly("l {password}");
        }
    }

    @Nested
    class SuccessPredicate {
        @Test
        void stepWaitsForSuccessCondition() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("trigger"))
                    .then("cmd")
                    .successWhen(p -> p.getData().contains("success"))
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("next"))
                    .then("cmd2")
                .add()
                .cooldown(10000)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("trigger"));
            assertThat(flow.getCurrentStepIndex()).isEqualTo(0);
            assertThat(sentCommands).containsExactly("cmd");

            // Match again but no success yet - cooldown prevents re-send
            flow.packetReceived(mockSession, new FakePacketA("trigger"));
            assertThat(flow.getCurrentStepIndex()).isEqualTo(0);
            assertThat(sentCommands).containsExactly("cmd");

            // Now it succeeds
            flow.packetReceived(mockSession, new FakePacketA("success"));
            assertThat(flow.getCurrentStepIndex()).isEqualTo(1);
        }

        @Test
        void successWithDifferentPacketType() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("trigger"))
                    .then("cmd")
                    .successWhen(FakePacketB.class, p -> p.getData().contains("done"))
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd2")
                .add()
                .cooldown(10000)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("trigger"));
            assertThat(flow.getCurrentStepIndex()).isEqualTo(0);
            assertThat(sentCommands).containsExactly("cmd");

            // FakePacketB with wrong data - not success
            flow.packetReceived(mockSession, new FakePacketB("nope"));
            assertThat(flow.getCurrentStepIndex()).isEqualTo(0);

            // FakePacketB with right data - success
            flow.packetReceived(mockSession, new FakePacketB("done"));
            assertThat(flow.getCurrentStepIndex()).isEqualTo(1);
        }

        @Test
        void onSuccessCallbackIsCalled() {
            AtomicReference<String> captured = new AtomicReference<>();

            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                    .onSuccess(p -> captured.set(p.getData()))
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("done"));

            assertThat(captured.get()).isEqualTo("done");
        }

        @Test
        void onSuccessCalledOnlyWhenMatched() {
            AtomicReference<String> captured = new AtomicReference<>();

            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("trigger"))
                    .then("cmd")
                    .onSuccess(p -> captured.set(p.getData()))
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("nope"));

            assertThat(captured.get()).isNull();
        }
    }

    @Nested
    class Reset {
        @Test
        void resetReturnsToInitialState() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd1")
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd2")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));
            assertThat(flow.getCurrentStepIndex()).isEqualTo(1);

            flow.reset();
            assertThat(flow.getCurrentStepIndex()).isEqualTo(0);
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.WAITING);

            flow.packetReceived(mockSession, new FakePacketA("x"));
            assertThat(sentCommands).containsExactly("cmd1", "cmd1");
        }

        @Test
        void resetAfterCompletedAllowsReuse() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);

            flow.packetReceived(mockSession, new FakePacketA("x"));
            assertThat(sentCommands).hasSize(1);

            flow.reset();
            flow.packetReceived(mockSession, new FakePacketA("x"));
            assertThat(sentCommands).hasSize(2);
        }
    }

    @Nested
    class AsListener {
        @Test
        void asListenerReturnsSameInstance() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                .add()
                .build();

            assertThat(flow.asListener()).isSameAs(flow);
        }
    }

    @Nested
    class StateChangeListener {
        @Test
        void stateChangeIsNotified() {
            List<LoginFlow.LoginFlowContext> changes = new ArrayList<>();

            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd2")
                .add()
                .cooldown(0)
                .onStateChange(changes::add)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));

            assertThat(changes).hasSize(1);
            assertThat(changes.get(0).stepIndex()).isEqualTo(1);
            assertThat(changes.get(0).state()).isEqualTo(LoginFlow.FlowState.WAITING);

            flow.packetReceived(mockSession, new FakePacketA("x"));

            assertThat(changes).hasSize(2);
            assertThat(changes.get(1).stepIndex()).isEqualTo(2);
            assertThat(changes.get(1).state()).isEqualTo(LoginFlow.FlowState.COMPLETED);
        }
    }

    @Nested
    class CompletedState {
        @Test
        void completedFlowIgnoresFurtherPackets() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("x"));
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);

            flow.packetReceived(mockSession, new FakePacketA("x"));
            flow.packetReceived(mockSession, new FakePacketA("x"));

            assertThat(sentCommands).hasSize(1);
        }
    }

    @Nested
    class Describe {
        @Test
        void descriptionDefaultsToClassName() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                .add()
                .build();

            // No way to directly test description, but it shouldn't throw
            assertThat(flow.getTotalSteps()).isEqualTo(1);
        }

        @Test
        void customDescription() {
            LoginFlow flow = baseBuilder
                .step(FakePacketA.class)
                    .match(p -> true)
                    .then("cmd")
                    .describe("My custom step")
                .add()
                .build();

            assertThat(flow.getTotalSteps()).isEqualTo(1);
        }
    }

    @Nested
    class RealWorldPattern {
        @Test
        void twoBtXinLoginPattern() {
            AtomicReference<String> loginEvent = new AtomicReference<>();

            LoginFlow flow = baseBuilder
                .templateExpander(t -> t.replace("{password}", "mypassword"))
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("注册"))
                    .then("reg {password} {password}")
                    .describe("Register")
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("登陆成功"))
                    .onSuccess(p -> loginEvent.set("success"))
                    .describe("Wait for login success")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("请注册"));
            assertThat(sentCommands).containsExactly("reg mypassword mypassword");
            assertThat(flow.getCurrentStepIndex()).isEqualTo(1);

            flow.packetReceived(mockSession, new FakePacketA("登陆成功"));
            assertThat(sentCommands).containsExactly("reg mypassword mypassword");
            assertThat(loginEvent.get()).isEqualTo("success");
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);
        }

        @Test
        void threeC3uLoginPattern() {
            AtomicReference<String> loginEvent = new AtomicReference<>();

            LoginFlow flow = baseBuilder
                .templateExpander(t -> t.replace("{password}", "pass123"))
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("使用指令登录"))
                    .then("l {password}")
                    .describe("Send login command")
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("成功登录"))
                    .onSuccess(p -> loginEvent.set("success"))
                    .describe("Wait for login success")
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("§c使用指令登录: /login <password>"));
            assertThat(sentCommands).containsExactly("l pass123");

            flow.packetReceived(mockSession, new FakePacketA("§2§l成功登录!"));
            assertThat(loginEvent.get()).isEqualTo("success");
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);
        }

        @Test
        void registerThenLoginThenJoinPattern() {
            List<String> events = new ArrayList<>();

            LoginFlow flow = baseBuilder
                .templateExpander(t -> t.replace("{password}", "pw123"))
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("register"))
                    .then("reg {password} {password}")
                    .onSuccess(p -> events.add("registered"))
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("login success"))
                    .onSuccess(p -> events.add("logged_in"))
                .step(FakePacketA.class)
                    .match(p -> p.getData().contains("join"))
                    .then("join")
                    .onSuccess(p -> events.add("joined"))
                .add()
                .cooldown(0)
                .build();

            flow.packetReceived(mockSession, new FakePacketA("please register"));
            flow.packetReceived(mockSession, new FakePacketA("login success"));
            flow.packetReceived(mockSession, new FakePacketA("click join"));

            assertThat(sentCommands).containsExactly("reg pw123 pw123", "join");
            assertThat(events).containsExactly("registered", "logged_in", "joined");
            assertThat(flow.getState()).isEqualTo(LoginFlow.FlowState.COMPLETED);
        }
    }
}
