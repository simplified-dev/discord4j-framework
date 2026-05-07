package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.DataType;
import dev.sbs.dataflow.DataTypes;
import dev.sbs.dataflow.stage.FieldSpec;
import dev.sbs.dataflow.stage.FieldType;
import dev.sbs.dataflow.stage.Stage;
import dev.sbs.dataflow.stage.StageConfig;
import dev.sbs.dataflow.stage.StageKind;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Bridge from a submitted {@link Modal} to a populated {@link StageConfig} ready for
 * {@link StageKind#factory()}.
 * <p>
 * Field text values are converted according to each {@link FieldSpec#type() FieldType};
 * unparseable values fail fast with a {@link Result#error()} string suitable for surfacing in
 * the builder's banner.
 * <p>
 * {@link FieldType#SUB_PIPELINES_MAP} fields are skipped here - branch sub-pipelines are
 * authored in a dedicated drill-down flow (step 2.7) and merged into the config separately.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageConfigParser {

    /**
     * Outcome of a parse attempt: either a populated configuration ready for the factory,
     * or an error message to surface in the builder banner.
     *
     * @param config the parsed configuration, {@code null} on error
     * @param error the error message, {@code null} on success
     */
    public record Result(@Nullable StageConfig config, @Nullable String error) {

        /** {@code true} when the parse succeeded. */
        public boolean ok() {
            return this.error == null;
        }

    }

    /**
     * Extracts the user-submitted text values from {@code modal} keyed by input identifier.
     *
     * @param modal the submitted modal carrying populated values
     * @return a map of {@code identifier -> submitted value}; missing or blank values are absent
     */
    public static @NotNull Map<String, String> readValues(@NotNull Modal modal) {
        Map<String, String> values = new HashMap<>();
        for (var component : modal.getComponents()) {
            if (!(component instanceof Label label)) continue;
            if (!(label.getComponent() instanceof TextInput input)) continue;
            input.getValue().ifPresent(v -> values.put(input.getIdentifier(), v));
        }
        return values;
    }

    /**
     * Attempts to build a {@link Stage} for {@code kind} from {@code values}, returning either
     * the constructed stage or an error message.
     *
     * @param kind the stage kind being configured
     * @param values the submitted text values keyed by {@link FieldSpec#name()}
     * @return the parse result
     */
    public static @NotNull Result parse(@NotNull StageKind kind, @NotNull Map<String, String> values) {
        if (kind.factory() == null)
            return new Result(null, "Stage kind '" + kind.name() + "' is not currently supported");
        StageConfig.Builder b = StageConfig.builder();
        for (FieldSpec spec : kind.schema()) {
            if (spec.type() == FieldType.SUB_PIPELINES_MAP) continue;
            String raw = values.get(spec.name());
            if (raw == null || raw.isEmpty()) {
                if (spec.type() == FieldType.STRING) {
                    b.string(spec.name(), "");
                    continue;
                }
                return new Result(null, "Missing value for '" + spec.label() + "'");
            }
            try {
                applyValue(b, spec, raw);
            } catch (NumberFormatException ex) {
                return new Result(null, "Invalid number for '" + spec.label() + "': " + raw);
            } catch (IllegalArgumentException ex) {
                return new Result(null, "'" + spec.label() + "': " + ex.getMessage());
            }
        }
        return new Result(b.build(), null);
    }

    private static void applyValue(@NotNull StageConfig.Builder b, @NotNull FieldSpec spec, @NotNull String raw) {
        switch (spec.type()) {
            case STRING -> b.string(spec.name(), raw);
            case INT -> b.integer(spec.name(), Integer.parseInt(raw.trim()));
            case LONG -> b.longVal(spec.name(), Long.parseLong(raw.trim()));
            case DOUBLE -> b.doubleVal(spec.name(), Double.parseDouble(raw.trim()));
            case BOOLEAN -> b.bool(spec.name(), parseBoolean(raw));
            case DATA_TYPE -> b.dataType(spec.name(), requireType(raw.trim()));
            case SUB_PIPELINES_MAP -> { /* handled outside */ }
        }
    }

    private static boolean parseBoolean(@NotNull String raw) {
        String trimmed = raw.trim().toLowerCase();
        return switch (trimmed) {
            case "true", "yes", "y", "1", "on" -> true;
            case "false", "no", "n", "0", "off" -> false;
            default -> throw new IllegalArgumentException("expected true/false, got '" + raw + "'");
        };
    }

    private static @NotNull DataType<?> requireType(@NotNull String label) {
        DataType<?> t = DataTypes.byLabel(label);
        if (t == null) throw new IllegalArgumentException("unknown DataType label '" + label + "'");
        return t;
    }

    /**
     * Convenience: parse and apply {@link StageKind#factory()} in one call.
     *
     * @param kind the stage kind
     * @param values the submitted text values
     * @return success carrying the new stage, or failure with a banner-ready message
     */
    public static @NotNull StageResult parseAndBuild(@NotNull StageKind kind, @NotNull Map<String, String> values) {
        Result parsed = parse(kind, values);
        if (!parsed.ok()) return new StageResult(null, parsed.error());
        Function<StageConfig, Stage<?, ?>> factory = kind.factory();
        try {
            Stage<?, ?> stage = factory.apply(parsed.config());
            return new StageResult(stage, null);
        } catch (RuntimeException ex) {
            return new StageResult(null, "Failed to build stage: " + ex.getMessage());
        }
    }

    /**
     * Outcome of a parse-and-build attempt.
     *
     * @param stage the built stage, {@code null} on error
     * @param error the error message, {@code null} on success
     */
    public record StageResult(@Nullable Stage<?, ?> stage, @Nullable String error) {

        /** {@code true} when the parse-and-build succeeded. */
        public boolean ok() {
            return this.error == null;
        }

    }

}
