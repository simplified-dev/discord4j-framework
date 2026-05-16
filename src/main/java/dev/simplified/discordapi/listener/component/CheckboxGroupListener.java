package dev.simplified.discordapi.listener.component;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.component.interaction.CheckboxGroup;
import dev.simplified.discordapi.context.component.CheckboxGroupContext;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.response.Response;
import dev.simplified.reflection.Reflection;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Listener for {@link CheckboxGroup} component interactions, updating the group's
 * selected values and constructing a {@link CheckboxGroupContext} for the handler.
 */
public final class CheckboxGroupListener extends ComponentListener<ComponentInteractionEvent, CheckboxGroupContext, CheckboxGroup> {

    /**
     * Constructs a new {@code CheckboxGroupListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public CheckboxGroupListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull CheckboxGroupContext getContext(@NotNull ComponentInteractionEvent event, @NotNull Response response, @NotNull CheckboxGroup component, @NotNull Optional<CachedResponse> followup) {
        List<String> values = event.getInteraction().getCommandInteraction()
            .flatMap(ci -> ci.getValues())
            .orElse(Concurrent.newList());

        component.updateSelected(values);

        return CheckboxGroupContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

    @Override
    protected @NotNull CheckboxGroupContext getEternalContext(@NotNull ComponentInteractionEvent event) {
        // CheckboxGroup's Builder requires a non-empty options list, which is
        // not meaningful for an eternal interaction. Instantiate the group
        // directly via reflection over its private all-args constructor.
        List<String> values = event.getInteraction().getCommandInteraction()
            .flatMap(ci -> ci.getValues())
            .orElse(Concurrent.newList());

        CheckboxGroup synthetic = new Reflection<>(CheckboxGroup.class).newInstance(
            event.getCustomId(),
            (ConcurrentList<?>) Concurrent.newUnmodifiableList(),
            0,
            0,
            false,
            false,
            Optional.<Function<CheckboxGroupContext, Mono<Void>>>empty(),
            (ConcurrentList<?>) Concurrent.newUnmodifiableList(),
            true
        );

        synthetic.updateSelected(values);

        return CheckboxGroupContext.ofEternal(
            this.getDiscordBot(),
            event,
            synthetic,
            computeEternalResponseId(event)
        );
    }

}
