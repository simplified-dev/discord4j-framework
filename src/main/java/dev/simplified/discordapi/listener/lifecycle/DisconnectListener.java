package dev.simplified.discordapi.listener.lifecycle;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.event.lifecycle.GatewayDisconnectBotEvent;
import dev.simplified.discordapi.listener.BotEventListener;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

/**
 * Listens for {@link GatewayDisconnectBotEvent} and shuts down the scheduler
 * to release virtual-thread executors when the gateway disconnects.
 */
public class DisconnectListener extends BotEventListener<GatewayDisconnectBotEvent> {

    /**
     * Constructs a new {@code DisconnectListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public DisconnectListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    public Publisher<Void> apply(@NotNull GatewayDisconnectBotEvent event) {
        return Mono.fromRunnable(() -> this.getDiscordBot().getScheduler().shutdown());
    }

}
