package dev.simplified.discordapi.listener.message;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.message.MessageDeleteEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;

/**
 * Listener for message delete events, removing the corresponding
 * {@link CachedResponse} entry (top-level or followup) from the response
 * locator when a tracked message is deleted.
 */
public class MessageDeleteListener extends DiscordListener<MessageDeleteEvent> {

    /**
     * Constructs a new {@code MessageDeleteListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public MessageDeleteListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public final @NotNull Publisher<Void> apply(@NotNull MessageDeleteEvent event) {
        return this.getDiscordBot()
            .getResponseLocator()
            .findByMessage(event.getMessageId())
            .flatMap(entry -> this.getDiscordBot().getResponseLocator().remove(entry.getUniqueId()));
    }

}
