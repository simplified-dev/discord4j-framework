package dev.simplified.discordapi.feature.extractor.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.command.parameter.Parameter;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.feature.extractor.Extractor;
import dev.simplified.discordapi.feature.extractor.ExtractorStore;
import dev.simplified.discordapi.feature.extractor.ui.PipelineBuilderResponse;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.Page;
import discord4j.common.util.Snowflake;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * {@code /extractor edit <name>} - loads a saved {@link Extractor} by short id and opens
 * the live pipeline builder seeded with its definition.
 */
@Structure(
    parent = @Structure.Parent(name = "extractor", description = "Author and manage data extractors"),
    name = "edit",
    description = "Edit a saved data extractor",
    ephemeral = true
)
public class ExtractorEditCommand extends DiscordCommand<SlashCommandContext> {

    /** Slash-option identifier for the extractor short id. */
    public static final @NotNull String OPTION_NAME = "name";

    /**
     * Constructs a new {@code /extractor edit} command.
     *
     * @param discordBot the bot instance
     */
    public ExtractorEditCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConcurrentList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList(
            Parameter.builder()
                .withName(OPTION_NAME)
                .withDescription("Short id of the saved extractor to edit")
                .withType(Parameter.Type.WORD)
                .isRequired()
                .withAutoComplete(autoCompleteContext -> {
                    long callerUserId = autoCompleteContext.getInteractUserId().asLong();
                    Long callerGuildId = autoCompleteContext.getGuildId().map(Snowflake::asLong).orElse(null);
                    ConcurrentMap<String, Object> values = Concurrent.newMap();
                    this.getDiscordBot().getExtractorStore()
                        .findVisible(callerUserId, callerGuildId)
                        .filter(row -> row.getOwnerUserId() == callerUserId)
                        .take(25)
                        .toStream()
                        .forEach(row -> values.put(row.getShortId(), row.getLabel()));
                    return values;
                })
                .build()
        );
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        String shortId = commandContext.getArgument(OPTION_NAME).orElseThrow().asString();
        long callerUserId = commandContext.getInteractUserId().asLong();
        Long callerGuildId = commandContext.getGuildId().map(Snowflake::asLong).orElse(null);
        ExtractorStore store = commandContext.getDiscordBot().getExtractorStore();

        return store.findByShortId(shortId, callerUserId, callerGuildId)
            .filter(row -> row.getOwnerUserId() == callerUserId)
            .flatMap(row -> commandContext.reply(PipelineBuilderResponse.forEditing(
                commandContext.getDiscordBot(), callerUserId, callerGuildId, row
            )))
            .switchIfEmpty(commandContext.reply(notFoundResponse(shortId)));
    }

    private static @NotNull Response notFoundResponse(@NotNull String shortId) {
        return Response.builder()
            .withTimeToLive(30)
            .isEphemeral()
            .withPages(Page.builder()
                .withContent("No extractor named `" + shortId + "` owned by you was found.")
                .build())
            .build();
    }

}
