package dev.simplified.discordapi.feature.extractor.command;

import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentList;
import dev.simplified.collection.ConcurrentMap;
import dev.simplified.dataflow.PipelineContext;
import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.command.Structure;
import dev.simplified.discordapi.command.parameter.Parameter;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.exception.DiscordException;
import dev.simplified.discordapi.feature.extractor.Extractor;
import dev.simplified.discordapi.feature.extractor.ExtractorResolver;
import dev.simplified.discordapi.feature.extractor.ExtractorRunner;
import dev.simplified.discordapi.feature.extractor.ExtractorStore;
import dev.simplified.discordapi.response.Response;
import dev.simplified.discordapi.response.page.Page;
import discord4j.common.util.Snowflake;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * {@code /extract <name>} - looks up a saved {@link Extractor} by short id, executes its
 * pipeline against a fresh {@link PipelineContext} carrying the calling user/guild ids in
 * its bag, and replies with the formatted result.
 * <p>
 * The command is global and ephemeral by default - results render only to the invoker.
 */
@Structure(
    name = "extract",
    description = "Run a saved data extractor",
    ephemeral = true
)
public class ExtractCommand extends DiscordCommand<SlashCommandContext> {

    /** Slash-option identifier for the extractor short id. */
    public static final @NotNull String OPTION_NAME = "name";

    /**
     * Constructs a new {@code /extract} command.
     *
     * @param discordBot the bot instance
     */
    public ExtractCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull ConcurrentList<Parameter> getParameters() {
        return Concurrent.newUnmodifiableList(
            Parameter.builder()
                .withName(OPTION_NAME)
                .withDescription("Short id of the saved extractor")
                .withType(Parameter.Type.WORD)
                .isRequired()
                .withAutoComplete(autoCompleteContext -> {
                    long callerUserId = autoCompleteContext.getInteractUserId().asLong();
                    Long callerGuildId = autoCompleteContext.getGuildId().map(Snowflake::asLong).orElse(null);
                    ConcurrentMap<String, Object> values = Concurrent.newMap();
                    this.getDiscordBot().getExtractorStore()
                        .findVisible(callerUserId, callerGuildId)
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

        return ExtractorRunner.run(store, shortId, callerUserId, callerGuildId,
                extractor -> PipelineContext.builder()
                    .withResolver(ExtractorResolver.of(store, PipelineContext.defaults()))
                    .withBagEntry(ExtractorResolver.BAG_CALLER_USER_ID, callerUserId)
                    .withBagEntry(ExtractorResolver.BAG_CALLER_GUILD_ID,
                        callerGuildId == null ? -1L : callerGuildId)
                    .build())
            .flatMap(result -> commandContext.reply(buildReply(shortId, result)));
    }

    private static @NotNull Response buildReply(@NotNull String shortId, @NotNull ExtractorRunner.Result result) {
        String body = result.ok()
            ? "**" + result.extractor().getLabel() + "** (`" + shortId + "`)\n" + result.formatted()
            : result.formatted();
        return Response.builder()
            .withTimeToLive(60)
            .isEphemeral()
            .withPages(Page.builder().withContent(body).build())
            .build();
    }

}
