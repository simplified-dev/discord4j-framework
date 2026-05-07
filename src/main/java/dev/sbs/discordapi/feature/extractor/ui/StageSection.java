package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.stage.Stage;
import dev.sbs.discordapi.component.TextDisplay;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.layout.Section;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders one {@link Stage} as a {@link Section}: a {@link TextDisplay} body showing the
 * stage's category icon, kind, summary, and type-arrow line, plus an {@code Edit} button
 * accessory wired to {@link PipelineBuilderPage#ID_EDIT_STAGE_PREFIX} {@code + index}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageSection {

    /**
     * Builds a section for the given stage with a no-op edit button (read-only render).
     *
     * @param index zero-based stage index used to wire the edit button identifier
     * @param stage the stage to render
     * @return the section
     */
    public static @NotNull Section of(int index, @NotNull Stage<?, ?> stage) {
        return of(index, stage, null);
    }

    /**
     * Builds a section for the given stage, attaching {@code onEdit} as the accessory's
     * click handler when non-null.
     *
     * @param index zero-based stage index used to wire the edit button identifier
     * @param stage the stage to render
     * @param onEdit the edit-button click handler, or {@code null} for a no-op button
     * @return the section
     */
    public static @NotNull Section of(int index, @NotNull Stage<?, ?> stage, @Nullable PipelineBuilderPage.IndexedHandler onEdit) {
        Button.Builder accessory = Button.builder()
            .withStyle(Button.Style.SECONDARY)
            .withLabel("Edit")
            .withIdentifier(PipelineBuilderPage.ID_EDIT_STAGE_PREFIX + index);
        if (onEdit != null) accessory.onInteract(ctx -> onEdit.handle(index, ctx));

        String body = "**" + icon(stage) + " " + categoryName(stage) + " #" + index + "** - "
            + stage.summary() + "\n"
            + "`" + stage.inputType().label() + " -> " + stage.outputType().label() + "`";

        return Section.builder()
            .withAccessory(accessory.build())
            .withComponents(TextDisplay.of(body))
            .build();
    }

    private static @NotNull String icon(@NotNull Stage<?, ?> stage) {
        return switch (stage.kind()) {
            case SOURCE_URL, SOURCE_PASTE -> ":globe_with_meridians:";
            case PARSE_HTML, PARSE_XML, PARSE_JSON -> ":scroll:";
            case BRANCH -> ":fork_and_knife:";
            case PIPELINE_EMBED -> ":link:";
            case COLLECT_FIRST, COLLECT_LAST, COLLECT_LIST, COLLECT_SET, COLLECT_JOIN -> ":inbox_tray:";
            default -> stage.kind().name().startsWith("FILTER_") ? ":mag:" : ":gear:";
        };
    }

    private static @NotNull String categoryName(@NotNull Stage<?, ?> stage) {
        String name = stage.kind().name();
        if (name.startsWith("SOURCE_")) return "Source";
        if (name.startsWith("PARSE_")) return "Parse";
        if (name.startsWith("FILTER_")) return "Filter";
        if (name.startsWith("TRANSFORM_")) return "Transform";
        if (name.startsWith("COLLECT_")) return "Collect";
        if ("BRANCH".equals(name)) return "Branch";
        if ("PIPELINE_EMBED".equals(name)) return "Embed";
        return name;
    }

}
