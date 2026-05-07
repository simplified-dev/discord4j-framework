package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive abstraction over the discord-api response cache. Default
 * implementation is an in-memory locator indexed by {@code messageId} and
 * {@code uniqueId} for O(1) lookup.
 *
 * @see CachedResponse
 */
public interface ResponseLocator {

    /**
     * Looks up a cached entry by Discord message snowflake.
     *
     * @param messageId the Discord message snowflake
     * @return a mono emitting the matching entry, or empty if none exists
     */
    Mono<CachedResponse> findByMessage(@NotNull Snowflake messageId);

    /**
     * Looks up a cached entry by its stable {@link Response#getUniqueId() response id}.
     *
     * @param responseId the stable response identifier
     * @return a mono emitting the matching entry, or empty if none exists
     */
    Mono<CachedResponse> findByResponseId(@NotNull UUID responseId);

    /**
     * Looks up a followup entry by its parent's response id and the
     * user-supplied followup identifier.
     *
     * @param parentId the {@link Response#getUniqueId() uniqueId} of the parent entry
     * @param identifier the user-supplied followup identifier
     * @return a mono emitting the matching followup entry, or empty if none exists
     */
    Mono<CachedResponse> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier);

    /**
     * Looks up a followup entry by its parent's response id and the followup's
     * Discord message snowflake.
     *
     * @param parentId the {@link Response#getUniqueId() uniqueId} of the parent entry
     * @param messageId the Discord message snowflake of the followup message
     * @return a mono emitting the matching followup entry, or empty if none exists
     */
    Mono<CachedResponse> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId);

    /**
     * Stores a newly-sent message and its {@link Response} in the cache.
     *
     * @param message the Discord message that was sent
     * @param creatorContext the context in which the message was created
     * @param response the response state being cached
     * @return a mono emitting the newly stored entry
     */
    Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response);

    /**
     * Stores a new followup as an independent top-level entry with
     * {@code parentId} pointing at the parent's {@link Response#getUniqueId()}.
     *
     * @param parent the parent cached entry
     * @param identifier the user-supplied followup identifier
     * @param message the newly-sent followup Discord message
     * @param creatorContext the context in which the followup was created
     * @param response the followup response state
     * @return a mono emitting the newly stored followup entry
     */
    Mono<CachedResponse> storeFollowup(
        @NotNull CachedResponse parent,
        @NotNull String identifier,
        @NotNull Message message,
        @NotNull EventContext<?> creatorContext,
        @NotNull Response response
    );

    /**
     * Removes an entry by stable response id. Cascades to followup entries
     * that reference this entry as their parent.
     *
     * @param responseId the stable response id to remove
     * @return a mono completing when removal finishes
     */
    Mono<Void> remove(@NotNull UUID responseId);

    /**
     * Persists mutable changes to an existing entry (state transitions,
     * lastInteractAt updates, and navigation state changes).
     *
     * @param entry the entry whose in-memory state has been mutated
     * @return a mono completing when the update finishes
     */
    Mono<Void> update(@NotNull CachedResponse entry);

    /**
     * Enumerates entries whose {@link CachedResponse#getExpiresAt() expiresAt}
     * has passed OR whose time-to-live has elapsed since the last interaction.
     * Used by the scheduled cleanup loop to disable components and evict
     * expired entries.
     *
     * @return a flux of expired entries
     */
    Flux<CachedResponse> findExpired();

}
