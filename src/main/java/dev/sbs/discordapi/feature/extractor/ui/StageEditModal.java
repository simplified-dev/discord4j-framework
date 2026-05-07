package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.DataType;
import dev.sbs.dataflow.stage.FieldSpec;
import dev.sbs.dataflow.stage.FieldType;
import dev.sbs.dataflow.stage.StageConfig;
import dev.sbs.dataflow.stage.StageKind;
import dev.sbs.discordapi.component.interaction.Modal;
import dev.sbs.discordapi.component.interaction.TextInput;
import dev.sbs.discordapi.component.layout.Label;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders the configuration {@link StageKind#schema() schema} of a {@link StageKind} as a
 * {@link Modal} of {@link Label}-wrapped {@link TextInput}s. Submitted values are routed back
 * through {@link StageKind#factory()} to reconstruct a fresh stage.
 * <p>
 * Wire format: each input's identifier matches the {@link FieldSpec#name()}, so handlers can
 * pull the submitted text by field name without bookkeeping. Stages with no schema yield a
 * single read-only {@link TextInput} - they are still added/edited via this modal so the
 * "Add Stage" flow always lands on a confirm-or-cancel surface.
 * <p>
 * {@link FieldType#SUB_PIPELINES_MAP} fields are skipped; stages with sub-pipelines (Branch)
 * use a dedicated drill-down flow added in step 2.7.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageEditModal {

    /** Component id prefix; suffix is {@code add.<KIND>} for new stages or {@code edit.<index>} for existing. */
    public static final @NotNull String ID_PREFIX = "extractor.builder.modal.";

    /** Identifier prefix for an "add new stage" submission, suffix is the {@link StageKind} name. */
    public static final @NotNull String ID_ADD_PREFIX = ID_PREFIX + "add.";

    /** Identifier prefix for an "edit existing stage" submission, suffix is the stage index. */
    public static final @NotNull String ID_EDIT_PREFIX = ID_PREFIX + "edit.";

    /**
     * Builds an "add stage" modal for the given kind, with all inputs blank.
     *
     * @param kind the stage kind being added
     * @return the modal
     */
    public static @NotNull Modal forAdd(@NotNull StageKind kind) {
        return build(kind, null, ID_ADD_PREFIX + kind.name(), "Add " + kind.displayName());
    }

    /**
     * Builds an "edit stage" modal pre-filled from {@code current}.
     *
     * @param index the zero-based stage index whose value is being edited
     * @param kind the stage kind
     * @param current the current configuration to pre-fill
     * @return the modal
     */
    public static @NotNull Modal forEdit(int index, @NotNull StageKind kind, @NotNull StageConfig current) {
        return build(kind, current, ID_EDIT_PREFIX + index, "Edit " + kind.displayName() + " #" + index);
    }

    private static @NotNull Modal build(@NotNull StageKind kind, @Nullable StageConfig prefill, @NotNull String identifier, @NotNull String title) {
        Modal.Builder modal = Modal.builder()
            .withIdentifier(identifier)
            .withTitle(title);

        for (FieldSpec spec : kind.schema()) {
            if (spec.type() == FieldType.SUB_PIPELINES_MAP) continue;

            TextInput input = TextInput.builder()
                .withIdentifier(spec.name())
                .withPlaceholder(spec.placeholder())
                .withValue(prefill == null ? null : renderValue(prefill.raw(spec.name()), spec.type()))
                .build();

            Label label = Label.builder()
                .withTitle(spec.label())
                .withComponent(input)
                .build();

            modal.withComponents(label);
        }

        return modal.build();
    }

    private static @Nullable String renderValue(@Nullable Object value, @NotNull FieldType type) {
        if (value == null) return null;
        return type == FieldType.DATA_TYPE ? ((DataType<?>) value).label() : String.valueOf(value);
    }

}
