package dev.sbs.discordapi.listener;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.command.DiscordCommand;
import dev.sbs.discordapi.response.PersistentResponse;
import dev.sbs.discordapi.util.DiscordReference;
import org.jetbrains.annotations.NotNull;

/**
 * Abstract base for classes hosting persistent component interaction handlers
 * and persistent response builders that are not tied to a specific
 * {@link DiscordCommand}.
 *
 * <p>
 * Subclasses are discovered via classpath scanning at startup, instantiated
 * once per bot with a constructor accepting a {@link DiscordBot}, and scanned
 * for methods annotated with {@link Component} and/or {@link PersistentResponse}.
 * The discovered routes are registered into the global component and builder
 * routing maps maintained by the persistent component handler.
 *
 * <p>
 * Use this base class for shared component handlers that apply across
 * multiple commands or for persistent response builders that are not logically
 * owned by any one command. Commands that own their own components and
 * builders can host {@code @Component} and {@code @PersistentResponse}
 * methods directly on their {@link DiscordCommand} subclass without needing
 * this base.
 *
 * @see Component
 * @see PersistentResponse
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
