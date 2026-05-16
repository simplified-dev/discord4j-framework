package dev.simplified.discordapi.listener.component;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.context.component.SelectMenuContext;
import dev.simplified.discordapi.handler.response.CachedResponse;
import dev.simplified.discordapi.response.Response;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Listener for {@link SelectMenu} component interactions, updating the menu's
 * selected values and constructing a {@link SelectMenuContext} for the handler.
 */
public final class SelectMenuListener extends ComponentListener<SelectMenuInteractionEvent, SelectMenuContext, SelectMenu> {

    /**
     * Constructs a new {@code SelectMenuListener} for the given bot.
     *
     * @param discordBot the bot instance
     */
    public SelectMenuListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

    @Override
    protected @NotNull SelectMenuContext getContext(@NotNull SelectMenuInteractionEvent event, @NotNull Response response, @NotNull SelectMenu component, @NotNull Optional<CachedResponse> followup) {
        return SelectMenuContext.of(
            this.getDiscordBot(),
            event,
            response,
            component.updateSelected(event.getValues()),
            followup
        );
    }

    @Override
    protected @NotNull SelectMenuContext getEternalContext(@NotNull SelectMenuInteractionEvent event) {
        SelectMenu synthetic = SelectMenu.StringMenu.builder()
            .withIdentifier(event.getCustomId())
            .build()
            .updateSelected(event.getValues());

        return SelectMenuContext.ofEternal(
            this.getDiscordBot(),
            event,
            synthetic,
            computeEternalResponseId(event)
        );
    }

}
