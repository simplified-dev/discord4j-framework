package dev.sbs.discordapi.handler.response;

import dev.sbs.discordapi.context.EventContext;
import dev.sbs.discordapi.handler.response.jpa.PersistentResponseEntity;
import dev.sbs.discordapi.response.Response;
import dev.simplified.persistence.JpaSession;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ComponentInteractionEvent;
import discord4j.core.object.entity.Message;
import jakarta.persistence.NoResultException;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Cold-tier {@link ResponseLocator} backed by a {@link JpaSession} writing
 * persistent response rows to the {@code discord_persistent_response} table.
 *
 * <p>
 * This locator is intentionally minimal: it implements only the operations
 * required for hydrating, persisting, and cascade-deleting
 * {@link PersistentResponseEntity} rows. Read-and-construct lookups (such as
 * {@link #findByMessage}) return {@link PersistentResponseEntity} as a synthetic
 * {@link CachedResponse} with the structural {@link Response} field set to
 * {@code null}-equivalent state - it is the {@link CompositeResponseLocator
 * composite locator}'s responsibility to invoke the matching
 * {@link dev.sbs.discordapi.response.PersistentResponse @PersistentResponse}
 * builder method to populate the actual {@link Response} before returning the
 * entry to callers. {@link #findForInteraction} is therefore unsupported here
 * and delegates to the message-id lookup.
 *
 * <p>
 * All blocking JPA operations run on {@link Schedulers#boundedElastic()} so
 * the reactor pipeline does not block its event loop.
 *
 * @see CompositeResponseLocator
 * @see PersistentResponseEntity
 */
public final class JpaResponseLocator implements ResponseLocator {

    private final @NotNull JpaSession jpaSession;

    /**
     * Constructs a new {@code JpaResponseLocator} backed by the given session.
     *
     * @param jpaSession the JPA session that owns the {@link PersistentResponseEntity} table
     */
    public JpaResponseLocator(@NotNull JpaSession jpaSession) {
        this.jpaSession = jpaSession;
    }

    /**
     * Reads the persistent row matching the given message snowflake and returns
     * it as a {@link PersistentRowResult} - a thin wrapper around the entity
     * that the composite locator passes to the hydration flow. The structural
     * {@link Response} is NOT yet populated; only the entity row is loaded.
     */
    public Mono<Optional<PersistentResponseEntity>> findEntityByMessage(@NotNull Snowflake messageId) {
        Function<Session, Optional<PersistentResponseEntity>> query = session -> {
            try {
                PersistentResponseEntity entity = session.createQuery(
                        "FROM PersistentResponseEntity WHERE messageId = :mid",
                        PersistentResponseEntity.class
                    )
                    .setParameter("mid", messageId.asLong())
                    .setMaxResults(1)
                    .getSingleResultOrNull();
                return Optional.ofNullable(entity);
            } catch (NoResultException ex) {
                return Optional.<PersistentResponseEntity>empty();
            }
        };
        return Mono.fromCallable(() -> this.jpaSession.with(query))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /** Reads the persistent row matching the given response id. */
    public Mono<Optional<PersistentResponseEntity>> findEntityById(@NotNull UUID responseId) {
        Function<Session, Optional<PersistentResponseEntity>> query = session ->
            Optional.ofNullable(session.find(PersistentResponseEntity.class, responseId));
        return Mono.fromCallable(() -> this.jpaSession.with(query))
            .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Persists the given cached entry as a {@link PersistentResponseEntity}
     * row. The caller is responsible for ensuring the entry is persistent
     * (has an owner class) before invoking this method.
     */
    public Mono<Void> persistEntry(@NotNull CachedResponse entry) {
        if (entry.getOwnerClass().isEmpty())
            return Mono.empty();

        return Mono.fromRunnable(() -> this.jpaSession.transaction((Session session) -> {
            PersistentResponseEntity entity = new PersistentResponseEntity();
            entity.setId(entry.getUniqueId());
            entity.setMessageId(entry.getMessageId().asLong());
            entity.setChannelId(entry.getChannelId().asLong());
            entity.setGuildId(entry.getGuildId().map(Snowflake::asLong).orElse(null));
            entity.setUserId(entry.getUserId().asLong());
            entity.setOwnerClass(entry.getOwnerClass().get().getName());
            entity.setBuilderId(entry.getBuilderId().orElse(""));
            entity.setNavState(entry.getNavState());
            entity.setFollowup(entry.isFollowup());
            entity.setParentId(entry.getParentId().orElse(null));
            entity.setFollowupIdentifier(entry.getFollowupIdentifier().orElse(null));
            entity.setCreatedAt(entry.getCreatedAt());
            entity.setLastInteractAt(Instant.ofEpochMilli(entry.getLastInteract()));
            entity.setExpiresAt(entry.getExpiresAt().orElse(null));
            session.persist(entity);
        })).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /**
     * Updates the navigation snapshot and last-interact timestamp for an
     * existing row. Used by the composite locator when an interaction
     * mutates the navigation state.
     */
    public Mono<Void> updateEntry(@NotNull CachedResponse entry) {
        if (entry.getOwnerClass().isEmpty())
            return Mono.empty();

        return Mono.fromRunnable(() -> this.jpaSession.transaction((Session session) -> {
            PersistentResponseEntity entity = session.find(PersistentResponseEntity.class, entry.getUniqueId());
            if (entity == null)
                return;
            entity.setNavState(entry.getNavState());
            entity.setLastInteractAt(Instant.ofEpochMilli(entry.getLastInteract()));
            session.merge(entity);
        })).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** Deletes a row by id and cascades to followup rows pointing at it. */
    public Mono<Void> deleteEntry(@NotNull UUID responseId) {
        return Mono.fromRunnable(() -> this.jpaSession.transaction((Session session) -> {
            session.createMutationQuery("DELETE FROM PersistentResponseEntity WHERE parentId = :pid")
                .setParameter("pid", responseId)
                .executeUpdate();
            session.createMutationQuery("DELETE FROM PersistentResponseEntity WHERE id = :id")
                .setParameter("id", responseId)
                .executeUpdate();
        })).subscribeOn(Schedulers.boundedElastic()).then();
    }

    /** Streams all rows whose {@code expiresAt} has passed. */
    public Flux<PersistentResponseEntity> findExpiredEntities() {
        Instant now = Instant.now();
        Function<Session, List<PersistentResponseEntity>> query = session -> session.createQuery(
                "FROM PersistentResponseEntity WHERE expiresAt IS NOT NULL AND expiresAt < :now",
                PersistentResponseEntity.class
            )
            .setParameter("now", now)
            .getResultList();
        return Mono.fromCallable(() -> this.jpaSession.with(query))
            .subscribeOn(Schedulers.boundedElastic())
            .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<CachedResponse> findByMessage(@NotNull Snowflake messageId) {
        // The cold tier alone cannot reconstruct a CachedResponse without
        // the matching @PersistentResponse builder method - that orchestration
        // lives in CompositeResponseLocator.
        return Mono.empty();
    }

    @Override
    public Mono<CachedResponse> findForInteraction(@NotNull ComponentInteractionEvent event) {
        return this.findByMessage(event.getMessageId());
    }

    @Override
    public Mono<CachedResponse> findByResponseId(@NotNull UUID responseId) {
        return Mono.empty();
    }

    @Override
    public Mono<CachedResponse> findFollowupByIdentifier(@NotNull UUID parentId, @NotNull String identifier) {
        return Mono.empty();
    }

    @Override
    public Mono<CachedResponse> findFollowupByMessage(@NotNull UUID parentId, @NotNull Snowflake messageId) {
        return Mono.empty();
    }

    @Override
    public Mono<CachedResponse> store(@NotNull Message message, @NotNull EventContext<?> creatorContext, @NotNull Response response) {
        // Cold-tier writes are driven by the composite locator after the hot
        // tier has produced the canonical CachedResponse instance.
        return Mono.empty();
    }

    @Override
    public Mono<CachedResponse> storeFollowup(
        @NotNull CachedResponse parent,
        @NotNull String identifier,
        @NotNull Message message,
        @NotNull EventContext<?> creatorContext,
        @NotNull Response response
    ) {
        return Mono.empty();
    }

    @Override
    public Mono<Void> remove(@NotNull UUID responseId) {
        return this.deleteEntry(responseId);
    }

    @Override
    public Mono<Void> update(@NotNull CachedResponse entry) {
        return this.updateEntry(entry);
    }

    @Override
    public Flux<CachedResponse> findExpired() {
        // The cold tier reports rows by entity; the composite locator decides
        // whether and how to materialize them as CachedResponse instances.
        return Flux.empty();
    }

    /** Wrapper exposing the loaded entity to the composite locator. */
    public record PersistentRowResult(@NotNull PersistentResponseEntity entity, @NotNull List<PersistentResponseEntity> followups) {}

    /**
     * Loads a persistent row by id along with all followups whose
     * {@code parentId} points at it. Used during eager hydration when the
     * composite locator wants to populate followup entries alongside the
     * parent.
     */
    public Mono<Optional<PersistentRowResult>> findRowWithFollowups(@NotNull UUID parentId) {
        Function<Session, Optional<PersistentRowResult>> query = session -> {
            PersistentResponseEntity entity = session.find(PersistentResponseEntity.class, parentId);
            if (entity == null)
                return Optional.<PersistentRowResult>empty();

            List<PersistentResponseEntity> followups = session.createQuery(
                    "FROM PersistentResponseEntity WHERE parentId = :pid",
                    PersistentResponseEntity.class
                )
                .setParameter("pid", parentId)
                .getResultList();
            return Optional.of(new PersistentRowResult(entity, followups));
        };
        return Mono.fromCallable(() -> this.jpaSession.with(query))
            .subscribeOn(Schedulers.boundedElastic());
    }

}
