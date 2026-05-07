package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.reflection.Reflection;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.function.Function;

/**
 * Listener for {@link Modal} submit interactions, matching the submitted modal
 * against the user's active modal stored on the matched {@link CachedResponse}
 * and delegating to its registered handler.
 */
public final class ModalListener extends ComponentListener<ModalSubmitInteractionEvent, ModalContext, Modal> {

    /**
     * Constructs a new {@code ModalListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public ModalListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ModalContext getContext(@NotNull ModalSubmitInteractionEvent event, @NotNull Response response, @NotNull Modal component, @NotNull Optional<CachedResponse> followup) {
        return ModalContext.of(
            this.getDiscordBot(),
            event,
            response,
            component,
            followup
        );
    }

    @Override
    protected @NotNull ModalContext getEternalContext(@NotNull ModalSubmitInteractionEvent event) {
        // Modal's Builder validation requires a non-empty title and components,
        // neither of which is meaningful for an eternal submit synthesized from
        // an event whose backing message has no cache entry. Instantiate the
        // Modal directly via reflection over its private all-args constructor
        // so the @Component handler still receives a Modal stub carrying the
        // submitted customId.
        Modal synthetic = new Reflection<>(Modal.class).newInstance(
            event.getCustomId(),
            Optional.<String>empty(),
            (ConcurrentList<?>) Concurrent.newUnmodifiableList(),
            (Function<ModalContext, Mono<Void>>) ctx -> ctx.deferEdit()
        );

        return ModalContext.ofEternal(
            this.getDiscordBot(),
            event,
            synthetic,
            computeEternalResponseId(event)
        );
    }

    @Override
    protected Mono<Void> handleEvent(@NotNull ModalSubmitInteractionEvent event, @NotNull CachedResponse entry) {
        Optional<CachedResponse> followup = entry.isFollowup() ? Optional.of(entry) : Optional.empty();
        CachedResponse target = followup.orElse(entry);

        return Mono.justOrEmpty(target.getUserModal(event.getInteraction().getUser()))
            .filter(modal -> event.getCustomId().equals(modal.getIdentifier()))
            .doOnNext(modal -> target.clearModal(event.getInteraction().getUser()))
            .flatMap(modal -> this.handleInteraction(event, entry, modal, followup))
            .then(entry.updateLastInteract())
            .then();
    }

}
