package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Reactive abstraction over the discord-api response cache that replaces the
 * legacy in-memory linear-scan list. The default implementation is a composite
 * of an in-memory hot tier (indexed by {@code messageId} and {@code uniqueId}
 * for O(1) lookup) and a JPA-backed cold tier for persistent responses that
 * survive bot restarts.
 *
 * <p>
 * Persistence branching is internal: {@link #store} inspects
 * {@link Response#isPersistent()} and routes to the correct tier(s). The
 * owner class and builder id for persistent writes are resolved from the
 * current reactor {@link reactor.util.context.Context Context} via
 * {@link dev.sbs.discordapi.handler.DispatchingClassContextKey}.
 *
 * <p>
 * The hydration flow for persistent messages is handled inside
 * {@link #findForInteraction}: on cold-tier hit with no matching hot entry,
 * the composite impl constructs a {@link dev.sbs.discordapi.context.HydrationContext
 * HydrationContext} from the incoming event, invokes the registered
 * {@link dev.sbs.discordapi.response.PersistentResponse @PersistentResponse}
 * method on the resolved owner instance, caches the reconstructed
 * {@link Response} in the hot tier, and returns the populated entry.
 * Callers never see the tier boundary.
 *
 * @see CachedResponse
 */
public interface ResponseLocator {

    /**
     * Looks up a cached entry by Discord message snowflake WITHOUT attempting
     * hydration. Used by cleanup paths and non-interactive lookups that
     * should not trigger a cold-tier read or a persistent response rebuild.
     *
     * @param messageId the Discord message snowflake
     * @return a mono emitting the matching entry, or empty if none exists
     */
    Mono<CachedResponse> findByMessage(@NotNull Snowflake messageId);

    /**
     * Looks up a cached entry for an incoming component interaction,
     * attempting hydration from the persistent store on hot-tier miss. The
     * provided event is used to construct a hydration context for invoking
     * the registered {@link dev.sbs.discordapi.response.PersistentResponse @PersistentResponse}
     * builder method when needed.
     *
     * @param event the incoming component interaction event
     * @return a mono emitting the matching (possibly hydrated) entry, or empty if none exists
     */
    Mono<CachedResponse> findForInteraction(@NotNull ComponentInteractionEvent event);

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
     * Stores a newly-sent message and its {@link Response}. Internally
     * branches on {@link Response#isPersistent()} to route either to the
     * hot tier only (temporary) or write-through to both tiers (persistent).
     * The owner class and builder id for persistent writes are read from
     * the reactor {@link reactor.util.context.Context Context}.
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
     * The followup inherits the parent's persistence mode and expiry by
     * default.
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
     * Removes an entry by stable response id. For persistent entries, also
     * deletes the row from the cold tier. Cascades to followup entries
     * that reference this entry as their parent.
     *
     * @param responseId the stable response id to remove
     * @return a mono completing when removal finishes
     */
    Mono<Void> remove(@NotNull UUID responseId);

    /**
     * Persists mutable changes to an existing entry (state transitions,
     * lastInteractAt updates, and navigation state changes). Persistent
     * entries write navigation changes through to the cold tier; transient
     * changes like lastInteractAt are hot-tier only.
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
