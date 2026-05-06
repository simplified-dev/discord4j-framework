package dev.sbs.discordapi.feature.extractor.jpa;

import dev.simplified.persistence.JpaSession;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Synchronous CRUD wrapper over {@link DataExtractor} rows. Caller is responsible for
 * scheduling onto a blocking-friendly executor (typically {@code Schedulers.boundedElastic()})
 * before invoking these methods from inside a reactive flow.
 * <p>
 * Visibility checks are enforced on every read - the repository never returns an extractor
 * the caller is not permitted to use.
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class DataExtractorRepository {

    private final @NotNull JpaSession jpaSession;

    /**
     * Constructs a repository against the given JPA session.
     *
     * @param jpaSession the session that owns the {@link DataExtractor} table
     * @return a new repository
     */
    public static @NotNull DataExtractorRepository of(@NotNull JpaSession jpaSession) {
        return new DataExtractorRepository(jpaSession);
    }

    /**
     * Loads the extractor with the given id and verifies the caller may use it.
     *
     * @param id the extractor id
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return the extractor, or empty when missing or inaccessible to the caller
     */
    public @NotNull Optional<DataExtractor> findById(@NotNull UUID id, long callerUserId, @Nullable Long callerGuildId) {
        DataExtractor row = this.jpaSession.with((Function<Session, DataExtractor>) session -> session.find(DataExtractor.class, id));
        if (row == null || !row.canBeUsedBy(callerUserId, callerGuildId))
            return Optional.empty();
        return Optional.of(row);
    }

    /**
     * Looks up an extractor by short id visible to the caller. Tries owner-private first,
     * then caller-guild, then public, returning the most specific match.
     *
     * @param shortId the extractor short id (the {@code /extract} lookup key)
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return the most-specific accessible extractor, or empty when no match exists
     */
    public @NotNull Optional<DataExtractor> findByShortId(@NotNull String shortId, long callerUserId, @Nullable Long callerGuildId) {
        return this.jpaSession.with((Function<Session, Optional<DataExtractor>>) session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DataExtractor> q = cb.createQuery(DataExtractor.class);
            Root<DataExtractor> root = q.from(DataExtractor.class);
            q.select(root).where(cb.equal(root.get("shortId"), shortId));
            List<DataExtractor> rows = session.createQuery(q).getResultList();
            // Owner-private match wins; then guild; then public.
            DataExtractor owner = null, guild = null, pub = null;
            for (DataExtractor row : rows) {
                switch (row.getVisibility()) {
                    case PRIVATE -> {
                        if (row.getOwnerUserId() == callerUserId && owner == null) owner = row;
                    }
                    case GUILD -> {
                        if (row.getGuildId() != null && row.getGuildId().equals(callerGuildId) && guild == null) guild = row;
                    }
                    case PUBLIC -> {
                        if (pub == null) pub = row;
                    }
                }
            }
            DataExtractor pick = owner != null ? owner : (guild != null ? guild : pub);
            return Optional.ofNullable(pick);
        });
    }

    /**
     * Lists every extractor the caller may use, sorted by name.
     *
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return the visible extractors
     */
    public @NotNull List<DataExtractor> findVisible(long callerUserId, @Nullable Long callerGuildId) {
        return this.jpaSession.with((Function<Session, List<DataExtractor>>) session -> {
            CriteriaBuilder cb = session.getCriteriaBuilder();
            CriteriaQuery<DataExtractor> q = cb.createQuery(DataExtractor.class);
            Root<DataExtractor> root = q.from(DataExtractor.class);

            List<Predicate> any = new ArrayList<>();
            any.add(cb.and(
                cb.equal(root.get("visibility"), DataExtractor.Visibility.PRIVATE),
                cb.equal(root.get("ownerUserId"), callerUserId)
            ));
            if (callerGuildId != null)
                any.add(cb.and(
                    cb.equal(root.get("visibility"), DataExtractor.Visibility.GUILD),
                    cb.equal(root.get("guildId"), callerGuildId)
                ));
            any.add(cb.equal(root.get("visibility"), DataExtractor.Visibility.PUBLIC));

            q.select(root).where(cb.or(any.toArray(Predicate[]::new))).orderBy(cb.asc(root.get("label")));
            return session.createQuery(q).getResultList();
        });
    }

    /**
     * Persists or updates an extractor. The {@link DataExtractor#getCreatedAt() createdAt}
     * field is filled in at insert if it is still {@link Instant#EPOCH}.
     *
     * @param extractor the extractor to save
     */
    public void save(@NotNull DataExtractor extractor) {
        this.jpaSession.transaction((Session session) -> {
            if (extractor.getCreatedAt().equals(Instant.EPOCH))
                extractor.setCreatedAt(Instant.now());
            session.merge(extractor);
        });
    }

    /**
     * Deletes the extractor with the given id, enforcing the caller's owner permission.
     * Non-owners cannot delete - use a moderation tool for that.
     *
     * @param id the extractor id
     * @param callerUserId the calling user's snowflake
     * @return {@code true} when a row was deleted, {@code false} when missing or not owned
     */
    public boolean deleteById(@NotNull UUID id, long callerUserId) {
        return Boolean.TRUE.equals(this.jpaSession.transaction((Session session) -> {
            DataExtractor row = session.find(DataExtractor.class, id);
            if (row == null || row.getOwnerUserId() != callerUserId) return false;
            session.remove(row);
            return true;
        }));
    }

}
