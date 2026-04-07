package dev.sbs.discordapi.listener.component;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.context.component.ModalContext;
import dev.sbs.discordapi.handler.response.CachedResponse;
import dev.sbs.discordapi.response.Response;
import discord4j.core.event.domain.interaction.ModalSubmitInteractionEvent;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;

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
