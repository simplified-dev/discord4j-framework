package dev.sbs.discordapi.event;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.listener.BotEventListener;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for events emitted by the {@link DiscordBot} itself, as
 * opposed to gateway events emitted by Discord4J.
 *
 * <p>
 * Bot events are pushed onto an internal stream owned by {@link DiscordBot}
 * and consumed by {@link BotEventListener} subclasses. Use this hierarchy for
 * lifecycle hooks and other internal notifications that must remain
 * inaccessible to downstream consumers holding a {@link DiscordBot} reference.
 *
 * @see BotEventListener
 */
public interface BotEvent {

    /** The bot instance that emitted this event. */
    @NotNull DiscordBot getDiscordBot();

}
