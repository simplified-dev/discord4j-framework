package dev.sbs.discordapi.feature.extractor.command;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.command.Structure;
import dev.sbs.discordapi.context.command.SlashCommandContext;
import dev.sbs.discordapi.exception.DiscordException;
import dev.sbs.discordapi.feature.extractor.ui.PipelineBuilderResponse;
import discord4j.common.util.Snowflake;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * {@code /extractor new} - opens an empty pipeline builder for the calling user.
 */
@Structure(
    parent = @Structure.Parent(name = "extractor", description = "Author and manage data extractors"),
    name = "new",
    description = "Open a fresh pipeline builder",
    ephemeral = true
)
public class ExtractorNewCommand extends DiscordCommand<SlashCommandContext> {

    /**
     * Constructs a new {@code /extractor new} command.
     *
     * @param discordBot the bot instance
     */
    public ExtractorNewCommand(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    /** {@inheritDoc} */
    @Override
    protected @NotNull Mono<Void> process(@NotNull SlashCommandContext commandContext) throws DiscordException {
        long callerUserId = commandContext.getInteractUserId().asLong();
        Long callerGuildId = commandContext.getGuildId().map(Snowflake::asLong).orElse(null);
        return commandContext.reply(PipelineBuilderResponse.startNew(
            commandContext.getDiscordBot(), callerUserId, callerGuildId
        ));
    }

}
