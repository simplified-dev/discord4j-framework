package dev.simplified.discordapi.feature.extractor;

import dev.simplified.discordapi.handler.DiscordConfig;
import dev.simplified.discordapi.handler.response.ResponseLocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Pluggable persistence boundary for {@link Extractor saved extractors}.
 * <p>
 * The framework defines this interface and ships {@link InMemoryExtractorStore} as a no-I/O
 * default. Consumers wire a database-backed implementation through
 * {@link DiscordConfig.Builder} when they need durability;
 * everything inside this module talks to the interface only.
 * <p>
 * Visibility is enforced inside the store: the read methods return only extractors the
 * caller is permitted to use. Callers therefore never have to repeat the
 * {@link Extractor#canBeUsedBy(long, Long)} check.
 * <p>
 * Reactive semantics mirror {@link ResponseLocator}:
 * empty {@link Mono} signals not-found; reactive types let consumers schedule blocking I/O
 * onto whichever scheduler suits their backend.
 */
public interface ExtractorStore {

    /**
     * Looks up an extractor by id, filtered by caller visibility.
     *
     * @param id the extractor id
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return the extractor, or empty when missing or inaccessible to the caller
     */
    @NotNull Mono<Extractor> findById(@NotNull UUID id, long callerUserId, @Nullable Long callerGuildId);

    /**
     * Looks up an extractor by short id, filtered by caller visibility. Owner-private match
     * wins, then guild, then public.
     *
     * @param shortId the {@code /extract} lookup key
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return the most-specific accessible extractor, or empty when no match exists
     */
    @NotNull Mono<Extractor> findByShortId(@NotNull String shortId, long callerUserId, @Nullable Long callerGuildId);

    /**
     * Lists every extractor the caller may use, sorted by label.
     *
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return a flux of accessible extractors
     */
    @NotNull Flux<Extractor> findVisible(long callerUserId, @Nullable Long callerGuildId);

    /**
     * Persists or updates an extractor. The store fills in {@link Extractor#getCreatedAt()}
     * when it is still {@link java.time.Instant#EPOCH}.
     *
     * @param extractor the extractor to save
     * @return a Mono completing when the write is durable
     */
    @NotNull Mono<Void> save(@NotNull Extractor extractor);

    /**
     * Deletes the extractor with the given id, enforcing the caller's owner permission.
     * Non-owners cannot delete via this method.
     *
     * @param id the extractor id
     * @param callerUserId the calling user's snowflake
     * @return a Mono of {@code true} when a row was deleted, {@code false} when missing or not owned
     */
    @NotNull Mono<Boolean> deleteById(@NotNull UUID id, long callerUserId);

}
