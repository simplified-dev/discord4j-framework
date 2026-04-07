package dev.sbs.discordapi.handler.response.jpa;

import dev.sbs.discordapi.handler.response.NavState;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * JPA entity backing the cold tier of the discord-api persistent response cache.
 *
 * <p>
 * Each row represents one Discord message - either a top-level reply or a followup -
 * whose owning {@link dev.sbs.discordapi.response.Response Response} was sent with
 * {@link dev.sbs.discordapi.response.Response.Builder#isPersistent(boolean) .isPersistent(true)}.
 * Followups are stored as independent rows with {@link #getParentId() parentId} pointing at
 * the parent's {@link #getId() id}; cascade cleanup is performed in
 * {@link dev.sbs.discordapi.handler.response.JpaResponseLocator JpaResponseLocator} since
 * the schema does not declare a foreign-key constraint.
 *
 * <p>
 * The minimal mutable state stored on every interaction is the
 * {@link NavState navigation state}, serialized to a JSON column via the persistence
 * library's {@link dev.simplified.persistence.type.GsonType @GsonType} support. The
 * structural content of the response (embeds, components, lambdas) is reconstructed on
 * hydration by invoking the matching
 * {@link dev.sbs.discordapi.response.PersistentResponse @PersistentResponse} builder
 * method on the registered owner class.
 *
 * @see dev.sbs.discordapi.handler.response.ResponseLocator
 * @see dev.sbs.discordapi.handler.response.CachedResponse
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
    name = "discord_persistent_response",
    indexes = {
        @Index(name = "idx_discord_persistent_response_message_id", columnList = "message_id", unique = true),
        @Index(name = "idx_discord_persistent_response_owner_class_builder", columnList = "owner_class, builder_id"),
        @Index(name = "idx_discord_persistent_response_expires_at", columnList = "expires_at"),
        @Index(name = "idx_discord_persistent_response_parent_id", columnList = "parent_id")
    }
)
public class PersistentResponseEntity implements JpaModel {

    /** Stable {@link UUID} matching the in-memory {@code Response.uniqueId} for this row. */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull UUID id = new UUID(0L, 0L);

    /** Discord message snowflake (raw long) for the message backing this row. */
    @Column(name = "message_id", nullable = false, unique = true)
    private long messageId;

    /** Discord channel snowflake (raw long) for the channel containing the message. */
    @Column(name = "channel_id", nullable = false)
    private long channelId;

    /** Discord guild snowflake (raw long), or {@code null} for direct-message contexts. */
    @Column(name = "guild_id")
    private @Nullable Long guildId;

    /** Discord user snowflake (raw long) of the user who created the response. */
    @Column(name = "user_id", nullable = false)
    private long userId;

    /**
     * Fully-qualified class name of the dispatching command or
     * {@link dev.sbs.discordapi.listener.PersistentComponentListener PersistentComponentListener}
     * hosting the {@link dev.sbs.discordapi.response.PersistentResponse @PersistentResponse}
     * builder for this row.
     */
    @Column(name = "owner_class", nullable = false, length = 256)
    private @NotNull String ownerClass = "";

    /**
     * Discriminator id when the owner class hosts multiple
     * {@link dev.sbs.discordapi.response.PersistentResponse @PersistentResponse} methods.
     * Empty string for the common single-builder case.
     */
    @Column(name = "builder_id", nullable = false, length = 64)
    private @NotNull String builderId = "";

    /**
     * Mutable navigation snapshot persisted across restarts. Defaults to an
     * {@link NavState#empty() empty} state for new rows.
     */
    @Column(name = "nav_state", nullable = false)
    private @NotNull NavState navState = NavState.empty();

    /**
     * {@code true} when this row represents a followup message rather than the
     * top-level response. Followups have a non-null {@link #parentId}.
     */
    @Column(name = "is_followup", nullable = false)
    private boolean followup;

    /**
     * Identifier of the parent row when this row is a followup, or {@code null}
     * for top-level rows.
     */
    @Column(name = "parent_id")
    private @Nullable UUID parentId;

    /**
     * Optional user-supplied followup identifier, used by
     * {@link dev.sbs.discordapi.context.scope.MessageContext#editFollowup(String, java.util.function.Function)
     * MessageContext#editFollowup(String, ...)} to address followups by name.
     */
    @Column(name = "followup_identifier", length = 64)
    private @Nullable String followupIdentifier;

    /** Timestamp at which this row was created. */
    @Column(name = "created_at", nullable = false)
    private @NotNull Instant createdAt = Instant.EPOCH;

    /** Timestamp of the most recent user interaction with this row. */
    @Column(name = "last_interact_at", nullable = false)
    private @NotNull Instant lastInteractAt = Instant.EPOCH;

    /**
     * Optional expiration timestamp. Rows whose {@code expiresAt} has passed are
     * eligible for removal by the scheduled cleanup loop. {@code null} indicates
     * the row never expires.
     */
    @Column(name = "expires_at")
    private @Nullable Instant expiresAt;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PersistentResponseEntity that = (PersistentResponseEntity) o;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.id);
    }

}
