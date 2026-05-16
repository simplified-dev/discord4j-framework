package dev.simplified.discordapi.listener.message;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.context.message.ReactionContext;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.response.Emoji;
import dev.simplified.discordapi.response.Response;
import discord4j.core.event.domain.message.ReactionAddEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Listener for reaction add events, constructing a {@link ReactionContext} with
 * {@link ReactionContext.Type#ADD} and delegating to the emoji's interaction handler.
 */
public final class ReactionAddListener extends ReactionListener<ReactionAddEvent> {

    /**
     * Constructs a new {@code ReactionAddListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public ReactionAddListener(DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull ReactionContext getContext(@NotNull ReactionAddEvent event, @NotNull Response cachedMessage, @NotNull Emoji emoji, @NotNull Optional<CachedResponse> followup) {
        return ReactionContext.of(
            this.getDiscordBot(),
            event,
            cachedMessage,
            emoji,
            ReactionContext.Type.ADD,
            followup
        );
    }

}
