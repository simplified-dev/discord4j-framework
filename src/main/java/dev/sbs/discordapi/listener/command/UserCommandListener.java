package dev.sbs.discordapi.listener.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.context.command.UserCommandContext;
import dev.sbs.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.UserInteractionEvent;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Listener for user context menu command interactions, resolving the target
 * {@link DiscordCommand} and delegating to {@link DiscordCommand#apply} with
 * a {@link UserCommandContext}.
 */
public final class UserCommandListener extends DiscordListener<UserInteractionEvent> {

    /**
     * Constructs a new {@code UserCommandListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public UserCommandListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    @SuppressWarnings("all")
    public @NotNull Publisher<Void> apply(@NotNull UserInteractionEvent event) {
        return Mono.justOrEmpty(event.getInteraction().getData().data().toOptional())
            .flatMapMany(commandData -> Flux.fromIterable(
                this.getDiscordBot()
                    .getCommandHandler()
                    .getCommandsById(event.getCommandId().asLong()))
            )
            .single()
            .map(command -> (DiscordCommand<UserCommandContext>) command)
            .flatMap(command -> command.apply(this.buildContext(command, event)))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Constructs the {@link UserCommandContext} for the given command.
     *
     * @param command the matched command
     * @param event the incoming interaction event
     * @return the constructed user command context
     */
    private @NotNull UserCommandContext buildContext(@NotNull DiscordCommand<UserCommandContext> command, @NotNull UserInteractionEvent event) {
        return UserCommandContext.of(
            this.getDiscordBot(),
            event,
            command.getStructure()
        );
    }

}
