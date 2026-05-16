package dev.simplified.discordapi.listener.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.parameter.Argument;
import dev.simplified.discordapi.command.parameter.Parameter;
import dev.simplified.discordapi.context.command.AutoCompleteContext;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.listener.DiscordListener;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Listener for slash command autocomplete interactions, resolving the focused
 * {@link Parameter} and responding with suggestion choices from its autocomplete handler.
 */
public final class AutoCompleteListener extends DiscordListener<ChatInputAutoCompleteEvent> {

    /**
     * Constructs a new {@code AutoCompleteListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public AutoCompleteListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    @SuppressWarnings("all")
    public @NotNull Publisher<Void> apply(@NotNull ChatInputAutoCompleteEvent event) {
        return Mono.justOrEmpty(event.getInteraction().getData().data().toOptional())
            .flatMapMany(commandData -> Flux.fromIterable(
                this.getDiscordBot()
                    .getCommandHandler()
                    .getCommandsById(event.getCommandId().asLong()))
                .filter(command -> this.matchesInteractionData(command, commandData))
            )
            .single()
            .map(command -> (DiscordCommand<SlashCommandContext>) command)
            .flatMap(slashCommand -> event.respondWithSuggestions(this.buildSuggestions(slashCommand, event)));
    }

    /**
     * Resolves the focused parameter's autocomplete handler and materializes its
     * entries into Discord's {@link ApplicationCommandOptionChoiceData} format,
     * returning an empty list if the focused option does not map to a registered parameter.
     *
     * @param slashCommand the matched slash command
     * @param event the incoming autocomplete event
     * @return the list of suggestion choices to send back to Discord
     */
    private @NotNull ConcurrentList<ApplicationCommandOptionChoiceData> buildSuggestions(@NotNull DiscordCommand<SlashCommandContext> slashCommand, @NotNull ChatInputAutoCompleteEvent event) {
        return slashCommand.getParameters()
            .findFirst(Parameter::getName, event.getFocusedOption().getName())
            .map(parameter -> parameter.getAutoComplete()
                .apply(this.buildContext(slashCommand, parameter, event))
                .stream()
                .map(entry -> ApplicationCommandOptionChoiceData.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .build()
                )
                .map(ApplicationCommandOptionChoiceData.class::cast)
                .collect(Concurrent.toList())
            )
            .orElse(Concurrent.newList());
    }

    /**
     * Constructs the {@link AutoCompleteContext} for the focused parameter.
     *
     * @param slashCommand the matched slash command
     * @param parameter the focused parameter whose autocomplete handler will run
     * @param event the incoming autocomplete event
     * @return the constructed autocomplete context
     */
    private @NotNull AutoCompleteContext buildContext(@NotNull DiscordCommand<SlashCommandContext> slashCommand, @NotNull Parameter parameter, @NotNull ChatInputAutoCompleteEvent event) {
        return AutoCompleteContext.of(
            this.getDiscordBot(),
            event,
            slashCommand.getStructure(),
            new Argument(
                event.getInteraction(),
                parameter,
                event.getFocusedOption().getValue().orElseThrow()
            )
        );
    }

}
