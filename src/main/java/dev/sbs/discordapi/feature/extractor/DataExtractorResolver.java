package dev.sbs.discordapi.feature.extractor;

import dev.sbs.dataflow.DataPipeline;
import dev.sbs.dataflow.DataPipelineResolver;
import dev.sbs.dataflow.PipelineContext;
import dev.sbs.discordapi.feature.extractor.jpa.DataExtractor;
import dev.sbs.discordapi.feature.extractor.jpa.DataExtractorRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * {@link DataPipelineResolver} backed by {@link DataExtractorRepository}, enforcing
 * {@link DataExtractor.Visibility} against the calling Discord user/guild.
 * <p>
 * The caller's identity is read from a {@link PipelineContext} bag the host attaches before
 * invoking {@link DataPipeline#execute(PipelineContext)}:
 * <ul>
 *     <li>{@link #BAG_CALLER_USER_ID} - {@link Long} user snowflake (required)</li>
 *     <li>{@link #BAG_CALLER_GUILD_ID} - {@link Long} guild snowflake, or absent for DM</li>
 * </ul>
 * Without a {@code BAG_CALLER_USER_ID} entry the resolver returns empty for every lookup,
 * since visibility checks cannot be performed safely.
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public final class DataExtractorResolver implements DataPipelineResolver {

    /** Bag key for the calling user's snowflake. Value type: {@link Long}. */
    public static final @NotNull String BAG_CALLER_USER_ID = "discord.caller.user.id";

    /** Bag key for the calling guild's snowflake, or absent for DM. Value type: {@link Long}. */
    public static final @NotNull String BAG_CALLER_GUILD_ID = "discord.caller.guild.id";

    private final @NotNull DataExtractorRepository repository;
    private final @NotNull PipelineContext ctx;

    /**
     * Constructs a resolver bound to the calling pipeline context, so visibility checks see
     * the caller stored in the context bag.
     *
     * @param repository the underlying extractor repository
     * @param ctx the pipeline context whose bag carries the caller's user/guild ids
     * @return a new resolver
     */
    public static @NotNull DataExtractorResolver of(@NotNull DataExtractorRepository repository, @NotNull PipelineContext ctx) {
        return new DataExtractorResolver(repository, ctx);
    }

    /** {@inheritDoc} */
    @Override
    public @NotNull Optional<DataPipeline> resolve(@NotNull String id) {
        Long userId = (Long) this.ctx.bag().get(BAG_CALLER_USER_ID);
        if (userId == null) return Optional.empty();
        Long guildId = (Long) this.ctx.bag().get(BAG_CALLER_GUILD_ID);

        UUID parsed;
        try {
            parsed = UUID.fromString(id);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        return this.repository.findById(parsed, userId, guildId).map(DataExtractor::pipeline);
    }

    /** {@inheritDoc} */
    @Override
    public @Nullable String idOf(@NotNull DataPipeline pipeline) {
        // Transient pipelines built in-process do not carry an id; only persisted DataExtractors do.
        // The resolver does not own a reverse map - cycle detection on transient pipelines is impossible
        // and intentionally so.
        return null;
    }

}
