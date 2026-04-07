package dev.sbs.discordapi.event.lifecycle;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.event.BotEvent;
import discord4j.core.DiscordClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Emitted by {@link DiscordBot} immediately after the underlying
 * {@link DiscordClient} has been initialized during the login phase, before
 * the gateway connection is established.
 */
@Getter
@RequiredArgsConstructor
public final class ClientCreatedBotEvent implements BotEvent {

    /** The bot instance that emitted this event. */
    private final @NotNull DiscordBot discordBot;

    /** The newly initialized Discord REST client. */
    private final @NotNull DiscordClient discordClient;

}
