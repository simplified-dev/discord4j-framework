package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.RadioGroup;
import dev.sbs.discordapi.context.component.RadioGroupContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * Listener for {@link RadioGroup} component interactions, updating the group's
 * selected value and constructing a {@link RadioGroupContext} for the handler.
 */
public final class RadioGroupListener extends ComponentListener<ComponentInteractionEvent, RadioGroupContext, RadioGroup> {

    /**
     * Constructs a new {@code RadioGroupListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public RadioGroupListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull RadioGroupContext getContext(@NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull RadioGroup component, @NotNull Optional<CachedResponse> followup) {
        return RadioGroupContext.of(
            this.getDiscordBot(),
            event,
            response,
            component.updateSelected(
                event.getInteraction()
                    .getCommandInteraction()
                    .flatMap(ci -> ci.getValues().map(values -> values.isEmpty() ? null : values.getFirst()))
                    .orElse(null)
            ),
            followup
        );
    }

    @Override
    protected @NotNull RadioGroupContext getEternalContext(@NotNull ComponentInteractionEvent event) {
        // RadioGroup's Builder requires a non-empty options list, which is not
        // meaningful for an eternal interaction. Instantiate the RadioGroup
        // directly via reflection over its private all-args constructor.
        String selectedValue = event.getInteraction()
            .getCommandInteraction()
            .flatMap(ci -> ci.getValues().map(values -> values.isEmpty() ? null : values.getFirst()))
            .orElse(null);

        RadioGroup synthetic = new Reflection<>(RadioGroup.class).newInstance(
            event.getCustomId(),
            (ConcurrentList<?>) Concurrent.newUnmodifiableList(),
            false,
            false,
            Optional.<Function<RadioGroupContext, Mono<Void>>>empty(),
            Optional.<Object>empty(),
            true
        );

        if (selectedValue != null)
            synthetic.updateSelected(selectedValue);

        return RadioGroupContext.ofEternal(
            this.getDiscordBot(),
            event,
            synthetic,
            computeEternalResponseId(event)
        );
    }

}
