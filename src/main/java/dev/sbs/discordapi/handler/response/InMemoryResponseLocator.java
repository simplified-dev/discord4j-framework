package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.handler.DispatchingClassContextKey;
import dev.sbs.discordapi.response.Response;
import dev.simplified.collection.Concurrent;
import dev.simplified.collection.ConcurrentMap;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Hot-tier {@link ResponseLocator} backed by two in-memory maps: a primary
 * {@code uniqueId -> CachedResponse} map and a {@code messageId -> uniqueId}
 * secondary index. Both lookups are O(1) and replace the legacy linear-scan
 * list that previously walked every entry on every component interaction.
 *
 * <p>
 * This locator does not perform persistent hydration on its own - on a miss
 * it simply returns empty. The {@link CompositeResponseLocator} layers a
 * cold-tier on top to provide cross-restart durability for entries whose
 * bound {@link Response#isPersistent()} is true.
 *
 * @see CompositeResponseLocator
 * @see JpaResponseLocator
 */
public final class InMemoryResponseLocator implements ResponseLocator {

    private final @NotNull ConcurrentMap<UUID, CachedResponse> entries = Concurrent.newMap();
    private final @NotNull ConcurrentMap<Snowflake, UUID> messageIndex = Concurrent.newMap();

    @Override
    public Mono<Optional<CachedResponse>> findByMessage(@NotNull Snowflake messageId) {
        UUID uniqueId = this.messageIndex.get(messageId);
        return Mono.just(Optional.ofNullable(uniqueId).map(this.entries::get));
    }

    @Override
    public Mono<Optional<CachedResponse>> findForInteraction(@NotNull ComponentInteractionEvent event) {
        return this.findByMessage(event.getMessageId());
    }

    @Override
    public Mono<Optional<CachedResponse>> findByResponseId(@NotNull UUID responseId) {
        return Mono.just(Optional.ofNullable(this.entries.get(responseId)));
    }

    @Override
    public Mono<Optional<CachedResponse>> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier) {
        return Mono.just(this.entries.values()
            .stream()
            .filter(entry -> entry.getParentId().filter(parentId::equals).isPresent())
            .filter(entry -> entry.getFollowupIdentifier().filter(identifier::equals).isPresent())
            .findFirst()
        );
    }

    @Override
    public Mono<Optional<CachedResponse>> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId) {
        return this.findByMessage(messageId)
            .map(opt -> opt.filter(entry -> entry.getParentId().filter(parentId::equals).isPresent()));
    }

    @Override
    public Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response) {
        return Mono.deferContextual(reactorCtx -> {
            CachedResponse.Builder builder = CachedResponse.builder()
                .withUniqueId(response.getUniqueId())
                .withMessageId(message.getId())
                .withChannelId(message.getChannelId())
                .withUserId(creatorContext.getInteractUserId())
                .withGuildId(creatorContext.getGuildId())
                .withResponse(response)
                .withCreatedAt(Instant.now())
                .withExpiresAt(this.computeExpiresAt(response));

            if (response.isPersistent()) {
                Class<?> ownerClass = reactorCtx.<Class<?>>getOrEmpty(DispatchingClassContextKey.KEY)
                    .orElseThrow(() -> new IllegalStateException(
                        "Persistent Response sent outside a dispatched command or @Component handler"
                    ));
                builder.withOwnerClass(ownerClass);
                builder.withBuilderId(response.getPersistentBuilderId().orElse(""));
            }

            return Mono.just(this.put(builder.build()));
        });
    }

    @Override
    public Mono<CachedResponse> storeFollowup(
        @NotNull CachedResponse parent,
        @NotNull String identifier,
        @NotNull Message message,
        @NotNull EventContext<?> creatorContext,
        @NotNull Response response
    ) {
        CachedResponse.Builder builder = CachedResponse.builder()
            .withUniqueId(response.getUniqueId())
            .withMessageId(message.getId())
            .withChannelId(message.getChannelId())
            .withUserId(creatorContext.getInteractUserId())
            .withGuildId(creatorContext.getGuildId())
            .withParentId(parent.getUniqueId())
            .withFollowupIdentifier(identifier)
            .withResponse(response)
            .withCreatedAt(Instant.now())
            .withExpiresAt(this.computeExpiresAt(response));

        // Followups inherit the parent's persistence metadata so the cold tier
        // can be cascade-deleted when the parent expires.
        parent.getOwnerClass().ifPresent(builder::withOwnerClass);
        parent.getBuilderId().ifPresent(builder::withBuilderId);

        return Mono.just(this.put(builder.build()));
    }

    @Override
    public Mono<Void> remove(@NotNull UUID responseId) {
        return Mono.fromRunnable(() -> {
            CachedResponse removed = this.entries.remove(responseId);
            if (removed != null)
                this.messageIndex.remove(removed.getMessageId());

            // Cascade-delete followups that referenced this entry
            this.entries.values()
                .stream()
                .filter(entry -> entry.getParentId().filter(responseId::equals).isPresent())
                .toList()
                .forEach(followup -> {
                    this.entries.remove(followup.getUniqueId());
                    this.messageIndex.remove(followup.getMessageId());
                });
        });
    }

    @Override
    public Mono<Void> update(@NotNull CachedResponse entry) {
        // Hot tier holds entries by reference; mutations on the entry object
        // are immediately visible. Nothing to do here.
        return Mono.empty();
    }

    @Override
    public Flux<CachedResponse> findExpired() {
        return Flux.fromIterable(this.entries.values())
            .filter(CachedResponse::notActive);
    }

    /**
     * Records the given entry in both maps and returns it. Public so the
     * composite locator can hand off freshly hydrated entries from the
     * cold tier without re-running persistence branching logic.
     */
    @NotNull CachedResponse put(@NotNull CachedResponse entry) {
        this.entries.put(entry.getUniqueId(), entry);
        this.messageIndex.put(entry.getMessageId(), entry.getUniqueId());
        return entry;
    }

    /**
     * Computes the optional expiration instant from the response's
     * time-to-live. Persistent responses with a builder-explicit
     * time-to-live still expire; non-positive values indicate no expiry.
     */
    private @NotNull Optional<Instant> computeExpiresAt(@NotNull Response response) {
        int ttl = response.getTimeToLive();
        if (ttl <= 0)
            return Optional.empty();

        return Optional.of(Instant.now().plus(Duration.ofSeconds(ttl)));
    }

}
