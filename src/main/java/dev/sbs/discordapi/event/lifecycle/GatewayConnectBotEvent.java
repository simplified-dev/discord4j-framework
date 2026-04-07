package dev.sbs.discordapi.event.lifecycle;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.event.BotEvent;
import discord4j.core.GatewayDiscordClient;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Emitted by {@link DiscordBot} when the Discord gateway connection has been
 * successfully established and the {@link GatewayDiscordClient} is available.
 */
@Getter
@RequiredArgsConstructor
public final class GatewayConnectBotEvent implements BotEvent {

    /** The bot instance that emitted this event. */
    private final @NotNull DiscordBot discordBot;

    /** The connected gateway client. */
    private final @NotNull GatewayDiscordClient gatewayDiscordClient;

}
