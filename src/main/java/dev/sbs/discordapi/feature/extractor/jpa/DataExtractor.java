package dev.sbs.discordapi.feature.extractor.jpa;

import dev.sbs.dataflow.DataPipeline;
import dev.sbs.dataflow.serde.PipelineGson;
import dev.simplified.persistence.JpaModel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Persistent record of a saved {@link DataPipeline}, stored independently of the Discord
 * builder UI cache so an extractor survives bot restarts.
 * <p>
 * The pipeline definition itself lives in {@link #definitionJson} as a JSON blob produced
 * by {@link PipelineGson}. {@link #pipeline()} and {@link #setPipeline(DataPipeline)} hide
 * the round-trip from callers.
 * <p>
 * Visibility is enforced at the repository layer via {@link #canBeUsedBy(long, Long)}:
 * {@link Visibility#PRIVATE} extractors are usable only by their owner; {@link Visibility#GUILD}
 * extractors are usable from the guild they were saved in; {@link Visibility#PUBLIC} extractors
 * are usable by anyone.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(
    name = "discord_data_extractor",
    indexes = {
        @Index(name = "idx_data_extractor_owner_user_id", columnList = "owner_user_id"),
        @Index(name = "idx_data_extractor_visibility", columnList = "visibility"),
        @Index(name = "idx_data_extractor_name_owner", columnList = "name, owner_user_id", unique = true)
    }
)
public class DataExtractor implements JpaModel {

    /** Stable {@link UUID} primary key. */
    @Id
    @Column(name = "id", nullable = false)
    private @NotNull UUID id = new UUID(0L, 0L);

    /** Discord user snowflake (raw long) of the user who saved this extractor. */
    @Column(name = "owner_user_id", nullable = false)
    private long ownerUserId;

    /** User-supplied short name. Unique per owner. */
    @Column(name = "name", nullable = false, length = 64)
    private @NotNull String name = "";

    /**
     * {@link DataPipeline} definition serialised by {@link PipelineGson}. Stored as a
     * top-level JSON array of stage descriptors; see the dataflow module's wire-format docs.
     */
    @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
    private @NotNull String definitionJson = "[]";

    /** Sharing scope. Defaults to {@link Visibility#PRIVATE}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private @NotNull Visibility visibility = Visibility.PRIVATE;

    /**
     * Discord guild snowflake (raw long) where this extractor was saved. Required when
     * {@link #visibility} is {@link Visibility#GUILD}; ignored otherwise.
     */
    @Column(name = "guild_id")
    private @Nullable Long guildId;

    /** Timestamp at which this extractor was created. */
    @Column(name = "created_at", nullable = false)
    private @NotNull Instant createdAt = Instant.EPOCH;

    /**
     * Deserialises the stored {@link #definitionJson} into a runtime {@link DataPipeline}.
     *
     * @return the rebuilt pipeline
     */
    public @NotNull DataPipeline pipeline() {
        return PipelineGson.fromJson(this.definitionJson);
    }

    /**
     * Serialises {@code pipeline} via {@link PipelineGson} and stores the result in
     * {@link #definitionJson}.
     *
     * @param pipeline the pipeline to persist
     */
    public void setPipeline(@NotNull DataPipeline pipeline) {
        this.definitionJson = PipelineGson.toJson(pipeline);
    }

    /**
     * Returns whether the caller is permitted to load this extractor.
     *
     * @param callerUserId the calling user's snowflake
     * @param callerGuildId the calling user's guild snowflake, or {@code null} in DM
     * @return {@code true} when the caller may load and use this extractor
     */
    public boolean canBeUsedBy(long callerUserId, @Nullable Long callerGuildId) {
        return switch (this.visibility) {
            case PRIVATE -> this.ownerUserId == callerUserId;
            case GUILD -> this.guildId != null && this.guildId.equals(callerGuildId);
            case PUBLIC -> true;
        };
    }

    /** Sharing scope of a saved extractor. */
    public enum Visibility {
        /** Owner-only access. */
        PRIVATE,
        /** Anyone in the same guild as the saver. */
        GUILD,
        /** Anyone, anywhere. */
        PUBLIC
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataExtractor that = (DataExtractor) o;
        return Objects.equals(this.id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.id);
    }

}
