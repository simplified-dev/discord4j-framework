package dev.simplified.discordapi.context.scope;

import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.context.capability.TypingContext;
import dev.simplified.discordapi.context.command.MessageCommandContext;
import dev.simplified.discordapi.context.command.SlashCommandContext;
import dev.simplified.discordapi.context.command.UserCommandContext;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ApplicationCommandInteractionEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Interaction scope for application command contexts, extending {@link DeferrableInteractionContext}
 * and {@link TypingContext} with access to the command identifier and resolved command type.
 *
 * <p>
 * This scope is the parent of all command interaction types - slash commands, user commands,
 * and message commands.
 *
 * @param <T> the specific {@link ApplicationCommandInteractionEvent} subtype
 * @see SlashCommandContext
 * @see UserCommandContext
 * @see MessageCommandContext
 */
public interface CommandContext<T extends ApplicationCommandInteractionEvent> extends DeferrableInteractionContext<T>, TypingContext<T> {

    /** The snowflake identifier of the invoked application command. */
    default @NotNull Snowflake getCommandId() {
        return this.getEvent().getCommandId();
    }

    /** {@inheritDoc} */
    @Override
    default @NotNull DiscordCommand.Type getType() {
        return DiscordCommand.Type.of(this.getEvent().getCommandType().getValue());
    }

}
