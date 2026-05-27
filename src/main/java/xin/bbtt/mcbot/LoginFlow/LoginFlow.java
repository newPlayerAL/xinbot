/*
 *   Copyright (C) 2024-2026 huangdihd
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package xin.bbtt.mcbot.LoginFlow;

import lombok.Getter;
import org.geysermc.mcprotocollib.network.Session;
import org.geysermc.mcprotocollib.network.event.session.SessionAdapter;
import org.geysermc.mcprotocollib.network.packet.Packet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A declarative, builder-based login flow state machine for MetaPlugins.
 * <p>
 * Instead of writing scattered {@code SessionAdapter} listeners with static boolean flags,
 * MetaPlugin authors can declare the entire login sequence as a chain of steps:
 * <pre>{@code
 * LoginFlow flow = LoginFlow.builder(Bot.INSTANCE::sendChatMessage)
 *     .templateExpander(t -> t.replace("{password}", config.getPassword()))
 *     .step(ClientboundSetTitleTextPacket.class)
 *         .match(p -> p.toString().contains("注册"))
 *         .then("reg {password} {password}")
 *     .step(ClientboundSetTitleTextPacket.class)
 *         .match(p -> p.toString().contains("登陆成功"))
 *         .onSuccess(p -> Bot.INSTANCE.getPluginManager().events().callEvent(new LoginSuccessEvent()))
 *     .cooldown(2000)
 *     .build();
 *
 * Bot.INSTANCE.addPacketListener(flow.asListener(), this);
 * }</pre>
 *
 * <h3>Key features</h3>
 * <ul>
 *   <li>Declarative step chain — each step declares "what to match" and "what to do"</li>
 *   <li>Built-in cooldown — prevents duplicate commands within a configurable window</li>
 *   <li>Template expansion — command templates with {@code {key}} placeholders</li>
 *   <li>Fires {@link xin.bbtt.mcbot.events.LoginFlowEvent} on state transitions (if registered)</li>
 *   <li>Decoupled from {@code Bot.INSTANCE} — accepts a command sender callback</li>
 * </ul>
 */
public class LoginFlow extends SessionAdapter {

    private static final Logger log = LoggerFactory.getLogger(LoginFlow.class.getSimpleName());

    private final List<LoginFlowStep<?, ?>> steps;
    private final long cooldownMs;
    private final Consumer<String> commandSender;
    private final Function<String, String> templateExpander;
    private final Consumer<LoginFlowContext> stateChangeListener;

    private int currentStepIndex = 0;
    private long lastCommandTime = 0;
    @Getter
    private FlowState state = FlowState.WAITING;

    LoginFlow(LoginFlowBuilder builder) {
        this.steps = Collections.unmodifiableList(builder.steps);
        this.cooldownMs = builder.cooldownMs;
        this.commandSender = builder.commandSender;
        this.templateExpander = builder.templateExpander;
        this.stateChangeListener = builder.stateChangeListener;
    }

    /**
     * Creates a new builder with a command sender.
     *
     * @param commandSender callback to send commands, e.g. {@code Bot.INSTANCE::sendChatMessage}
     */
    public static LoginFlowBuilder builder(Consumer<String> commandSender) {
        return new LoginFlowBuilder(Objects.requireNonNull(commandSender, "commandSender"));
    }

    @Override
    public void packetReceived(Session session, Packet packet) {
        if (state == FlowState.COMPLETED) return;
        if (currentStepIndex >= steps.size()) return;

        long now = System.currentTimeMillis();
        LoginFlowStep<?, ?> currentStep = steps.get(currentStepIndex);

        boolean isTriggerPacket = currentStep.packetClass.isInstance(packet);
        boolean isSuccessPacket = currentStep.successPacketClass != null
                && currentStep.successPacketClass.isInstance(packet);

        if (!isTriggerPacket && !isSuccessPacket) return;

        if (currentStep.successPredicate == null) {
            if (!isTriggerPacket || !currentStep.matches(packet)) return;
            sendCommandIfReady(currentStep, now);
            advanceStep(packet);
            return;
        }

        if (isTriggerPacket && currentStep.matches(packet)) {
            sendCommandIfReady(currentStep, now);
        }

        if (isSuccessPacket && currentStep.isSuccess(packet)) {
            advanceStep(packet);
        }
    }

    private void sendCommandIfReady(LoginFlowStep<?, ?> step, long now) {
        if (step.commandTemplate != null && now - lastCommandTime >= cooldownMs) {
            commandSender.accept(expandTemplate(step.commandTemplate));
            lastCommandTime = now;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void advanceStep(Packet packet) {
        LoginFlowStep step = steps.get(currentStepIndex);
        if (step.onSuccess != null) {
            ((Consumer) step.onSuccess).accept(packet);
        }

        currentStepIndex++;
        state = currentStepIndex >= steps.size() ? FlowState.COMPLETED : FlowState.WAITING;
        fireStateChange();
    }

    private void fireStateChange() {
        if (stateChangeListener != null) {
            stateChangeListener.accept(new LoginFlowContext(currentStepIndex, state));
        }
    }

    private String expandTemplate(String template) {
        return templateExpander != null ? templateExpander.apply(template) : template;
    }

    /**
     * Resets the flow to the initial state.
     */
    public void reset() {
        currentStepIndex = 0;
        lastCommandTime = 0;
        state = FlowState.WAITING;
    }

    /**
     * Returns the current step index (0-based).
     */
    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    /**
     * Returns the total number of steps.
     */
    public int getTotalSteps() {
        return steps.size();
    }

    /**
     * Returns this instance as a {@link SessionAdapter} for use with
     * {@code Bot.INSTANCE.addPacketListener()}.
     */
    public SessionAdapter asListener() {
        return this;
    }

    public enum FlowState {
        WAITING,
        COMPLETED
    }

    /**
     * Immutable snapshot of the login flow state at a point in time.
     */
    public record LoginFlowContext(int stepIndex, FlowState state) {}
}
