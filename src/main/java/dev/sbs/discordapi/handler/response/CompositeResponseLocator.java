package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.DiscordBot;
import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.context.HydrationContext;
import dev.sbs.discordapi.handler.PersistentComponentHandler;
import dev.sbs.discordapi.handler.response.jpa.PersistentResponseEntity;
import dev.sbs.discordapi.response.Response;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Two-tier {@link ResponseLocator} that wraps an {@link InMemoryResponseLocator
 * hot tier} and an optional {@link JpaResponseLocator cold tier}, exposing a
 * single facade so callers never see the tier boundary. Persistence branching
 * (writes to the cold tier when {@link Response#isPersistent()} is true) and
 * hydration (reads from the cold tier on a hot-tier miss) are handled
 * internally.
 *
 * <p>
 * The hydration flow on a hot-tier miss looks up the persistent row by
 * message id, finds the registered
 * {@link dev.sbs.discordapi.response.PersistentResponse @PersistentResponse}
 * builder via {@link PersistentComponentHandler}, invokes it with a
 * {@link HydrationContext} constructed from the incoming event to rebuild
 * the {@link Response} structure, restores the persisted navigation state,
 * and seeds the hot tier so subsequent interactions skip the cold-tier
 * round trip.
 *
 * @see InMemoryResponseLocator
 * @see JpaResponseLocator
 * @see PersistentComponentHandler
 */
@Log4j2
public final class CompositeResponseLocator implements ResponseLocator {

    private final @NotNull DiscordBot discordBot;
    private final @NotNull InMemoryResponseLocator hot;
    private final @NotNull JpaResponseLocator cold;
    private final @NotNull PersistentComponentHandler componentHandler;

    public CompositeResponseLocator(
        @NotNull DiscordBot discordBot,
        @NotNull InMemoryResponseLocator hot,
        @NotNull JpaResponseLocator cold,
        @NotNull PersistentComponentHandler componentHandler
    ) {
        this.discordBot = discordBot;
        this.hot = hot;
        this.cold = cold;
        this.componentHandler = componentHandler;
    }

    @Override
    public Mono<Optional<CachedResponse>> findByMessage(@NotNull Snowflake messageId) {
        return this.hot.findByMessage(messageId);
    }

    @Override
    public Mono<Optional<CachedResponse>> findForInteraction(@NotNull ComponentInteractionEvent event) {
        return this.hot.findByMessage(event.getMessageId())
            .flatMap(opt -> opt.isPresent()
                ? Mono.just(opt)
                : this.hydrateFromCold(event)
            );
    }

    /**
     * Loads a persistent row by message id and reconstructs a
     * {@link CachedResponse} by invoking the registered builder method.
     */
    private Mono<Optional<CachedResponse>> hydrateFromCold(@NotNull ComponentInteractionEvent event) {
        return this.cold.findEntityByMessage(event.getMessageId())
            .flatMap(opt -> opt
                .map(entity -> this.materializeEntity(event, entity).map(Optional::of))
                .orElseGet(() -> Mono.just(Optional.empty()))
            );
    }

    /**
     * Materializes a {@link PersistentResponseEntity} into a {@link CachedResponse}
     * by locating the registered builder route, invoking it via
     * {@link HydrationContext}, restoring navigation state, and seeding the
     * hot tier.
     */
    private Mono<CachedResponse> materializeEntity(@NotNull ComponentInteractionEvent event, @NotNull PersistentResponseEntity entity) {
        Class<?> ownerClass;
        try {
            ownerClass = Class.forName(entity.getOwnerClass());
        } catch (ClassNotFoundException ex) {
            log.error("persistent row references unknown owner class '{}', skipping hydration", entity.getOwnerClass(), ex);
            return Mono.empty();
        }

        Optional<PersistentComponentHandler.BuilderRoute> route = this.componentHandler.findBuilder(ownerClass, entity.getBuilderId());
        if (route.isEmpty()) {
            log.error(
                "persistent row '{}' references unregistered @PersistentResponse builder ({}, '{}'), skipping hydration",
                entity.getId(),
                ownerClass.getName(),
                entity.getBuilderId()
            );
            return Mono.empty();
        }

        return Mono.fromCallable(() -> {
            HydrationContext hydrationContext = HydrationContext.of(this.discordBot, event, entity.getId());
            Response rebuilt;
            try {
                rebuilt = (Response) route.get().getMethodHandle().invoke(route.get().getInstance(), hydrationContext);
            } catch (Throwable t) {
                throw new IllegalStateException(
                    "Failed to invoke @PersistentResponse builder " + ownerClass.getName() + " for row " + entity.getId(),
                    t
                );
            }

            log.info("hydrated persistent response '{}' from cold tier ({})", entity.getId(), ownerClass.getSimpleName());

            CachedResponse.Builder builder = CachedResponse.builder()
                .withUniqueId(entity.getId())
                .withMessageId(Snowflake.of(entity.getMessageId()))
                .withChannelId(Snowflake.of(entity.getChannelId()))
                .withUserId(Snowflake.of(entity.getUserId()))
                .withGuildId(Optional.ofNullable(entity.getGuildId()).map(Snowflake::of))
                .withResponse(rebuilt)
                .withOwnerClass(ownerClass)
                .withBuilderId(entity.getBuilderId())
                .withCreatedAt(entity.getCreatedAt())
                .withLastInteract(entity.getLastInteractAt().toEpochMilli())
                .withNavState(entity.getNavState())
                .withExpiresAt(Optional.ofNullable(entity.getExpiresAt()))
                .withState(CachedResponse.State.IDLE);

            if (entity.isFollowup() && entity.getParentId() != null)
                builder.withParentId(entity.getParentId());

            if (entity.getFollowupIdentifier() != null)
                builder.withFollowupIdentifier(entity.getFollowupIdentifier());

            return this.hot.put(builder.build());
        });
    }

    @Override
    public Mono<Optional<CachedResponse>> findByResponseId(@NotNull UUID responseId) {
        return this.hot.findByResponseId(responseId);
    }

    @Override
    public Mono<Optional<CachedResponse>> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier) {
        return this.hot.findFollowupByIdentifier(parentId, identifier);
    }

    @Override
    public Mono<Optional<CachedResponse>> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId) {
        return this.hot.findFollowupByMessage(parentId, messageId);
    }

    @Override
    public Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response) {
        return this.hot.store(message, creatorContext, response)
            .flatMap(entry -> {
                if (entry.isPersistent())
                    return this.cold.persistEntry(entry).thenReturn(entry);
                return Mono.just(entry);
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
        return this.hot.storeFollowup(parent, identifier, message, creatorContext, response)
            .flatMap(entry -> {
                if (entry.isPersistent())
                    return this.cold.persistEntry(entry).thenReturn(entry);
                return Mono.just(entry);
            });
    }

    @Override
    public Mono<Void> remove(@NotNull UUID responseId) {
        return this.hot.findByResponseId(responseId)
            .flatMap(opt -> {
                Mono<Void> hotRemove = this.hot.remove(responseId);
                Mono<Void> coldRemove = opt.filter(CachedResponse::isPersistent).isPresent()
                    ? this.cold.deleteEntry(responseId)
                    : Mono.empty();
                return hotRemove.then(coldRemove);
            });
    }

    @Override
    public Mono<Void> update(@NotNull CachedResponse entry) {
        if (entry.isPersistent())
            return this.cold.updateEntry(entry);
        return Mono.empty();
    }

    @Override
    public Flux<CachedResponse> findExpired() {
        // Hot-tier expiry covers transient messages and any persistent rows
        // already loaded into memory. Cold-only expirations are reaped by a
        // separate cold-tier sweep.
        return this.hot.findExpired();
    }

    /**
     * Streams persistent rows whose {@code expiresAt} has passed but which
     * are not currently in the hot tier, so the cleanup loop can delete
     * them without disturbing in-memory state.
     */
    public Flux<PersistentResponseEntity> findExpiredColdEntities() {
        Instant now = Instant.now();
        return this.cold.findExpiredEntities()
            .filter(entity -> entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(now));
    }

}
