package dev.simplified.discordapi.listener;

import dev.simplified.discordapi.DiscordBot;
import dev.simplified.discordapi.command.DiscordCommand;
import dev.simplified.discordapi.util.DiscordReference;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base for classes hosting annotation-dispatched component interaction
 * handlers that are not tied to a specific {@link DiscordCommand}.
 *
 * <p>
 * Subclasses are discovered via classpath scanning at startup, instantiated
 * once per bot with a constructor accepting a {@link DiscordBot}, and scanned
 * for methods annotated with {@link Component @Component}. The discovered
 * routes are registered into the global component dispatcher.
 *
 * <p>
 * Use this base class for shared component handlers that apply across multiple
 * commands, or for eternal/admin messages whose components must remain
 * functional across bot restarts. Commands that own their components inline
 * may host {@code @Component}-annotated methods directly on their
 * {@link DiscordCommand} subclass without needing this base.
 *
 * @see Component
 * @see DiscordReference
 */
public abstract class PersistentComponentListener extends DiscordReference {

    /**
     * Constructs a new {@code PersistentComponentListener} bound to the given bot instance.
     *
     * @param discordBot the bot instance this listener belongs to
     */
    public PersistentComponentListener(@NotNull DiscordBot discordBot) {
        super(discordBot);
    }

}
