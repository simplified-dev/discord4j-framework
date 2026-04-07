package dev.sbs.discordapi.context;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.scope.MessageContext;
import dev.sbs.discordapi.response.PersistentResponse;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.object.entity.channel.MessageChannel;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.util.Optional;
import java.util.UUID;

/**
 * Lightweight {@link EventContext} used during the persistent response hydration
 * flow, wrapping a {@link DiscordBot} and a raw Discord4J
 * {@link ComponentInteractionEvent}. Constructed by the response locator on a
 * cold-tier hit when the in-memory cache has no matching entry, then handed to
 * the matching {@link PersistentResponse @PersistentResponse} builder method to
 * reconstruct a {@link Response} from Discord-side information alone.
 *
 * <p>
 * This context intentionally does NOT extend {@link MessageContext}, so the
 * builder method has no static path to {@link MessageContext#getResponse()}
 * and cannot accidentally read from a cache entry that does not yet exist.
 * The reconstructed {@link Response} must be assembled purely from
 * {@link #getDiscordBot()}, {@link #getGuild()}, {@link #getChannel()}, and
 * {@link #getInteractUser()}, all of which are derived from the raw event.
 *
 * @see PersistentResponse
 * @see dev.sbs.discordapi.handler.response.ResponseLocator
 */
@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class HydrationContext implements EventContext<ComponentInteractionEvent> {

    /** The bot instance receiving the interaction. */
    private final @NotNull DiscordBot discordBot;

    /** The raw Discord4J component interaction event being hydrated. */
    private final @NotNull ComponentInteractionEvent event;

    /**
     * The stable {@link Response#getUniqueId() response id} of the persistent
     * row being hydrated. Carried so that any reconstructed {@link Response}
     * can adopt the same identifier and remain stable across restarts.
     */
    private final @NotNull UUID responseId;

    /**
     * Creates a new {@code HydrationContext} for the given bot, event, and
     * persistent row id.
     *
     * @param discordBot the bot instance
     * @param event the raw component interaction event triggering hydration
     * @param responseId the stable id of the persistent row being hydrated
     * @return a new hydration context
     */
    public static @NotNull HydrationContext of(@NotNull DiscordBot discordBot, @NotNull ComponentInteractionEvent event, @NotNull UUID responseId) {
        return new HydrationContext(discordBot, event, responseId);
    }

    /** {@inheritDoc} */
    @Override
    public Mono<MessageChannel> getChannel() {
        return this.event.getInteraction().getChannel();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Snowflake getChannelId() {
        return this.event.getInteraction().getChannelId();
    }

    /** {@inheritDoc} */
    @Override
    public Mono<Guild> getGuild() {
        return this.event.getInteraction().getGuild();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Snowflake> getGuildId() {
        return this.event.getInteraction().getGuildId();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull User getInteractUser() {
        return this.event.getInteraction().getUser();
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Snowflake getInteractUserId() {
        return this.event.getInteraction().getUser().getId();
    }

}
