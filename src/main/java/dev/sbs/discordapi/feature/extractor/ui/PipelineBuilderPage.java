package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.DataPipeline;
import dev.sbs.dataflow.ValidationReport;
import dev.sbs.dataflow.stage.Stage;
import dev.sbs.discordapi.component.TextDisplay;
import dev.sbs.discordapi.component.interaction.Button;
import dev.sbs.discordapi.component.layout.ActionRow;
import dev.sbs.discordapi.component.layout.Container;
import dev.sbs.discordapi.component.layout.Section;
import dev.sbs.discordapi.component.layout.Separator;
import dev.sbs.discordapi.response.page.Page;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Renders a {@link PipelineBuilderState} into a {@link Page}.
 * <p>
 * Layout:
 * <ul>
 *     <li>Title {@link TextDisplay} with the label or {@code (unsaved)}</li>
 *     <li>One {@link Section} per stage, accessory = an {@code Edit} button</li>
 *     <li>{@link Separator} divider</li>
 *     <li>Live-preview {@link Section} showing validation status or last result</li>
 *     <li>Footer {@link ActionRow}: {@code Add Stage}, {@code Run}, {@code Save as...},
 *     {@code Reset}, {@code Close}</li>
 * </ul>
 * <p>
 * In step 2.4 every interactive component is rendered without an
 * {@link Button#onInteract} handler - this is the read-only visual baseline; later steps
 * (2.5 onward) wire the handlers in.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class PipelineBuilderPage {

    /** Component identifier for the per-stage edit button. Suffix is the stage index. */
    public static final @NotNull String ID_EDIT_STAGE_PREFIX = "extractor.builder.edit-stage.";

    /** Component identifier for the {@code Add Stage} footer button. */
    public static final @NotNull String ID_ADD_STAGE = "extractor.builder.add-stage";

    /** Component identifier for the {@code Run} footer button. */
    public static final @NotNull String ID_RUN = "extractor.builder.run";

    /** Component identifier for the {@code Save as...} footer button. */
    public static final @NotNull String ID_SAVE = "extractor.builder.save";

    /** Component identifier for the {@code Reset} footer button. */
    public static final @NotNull String ID_RESET = "extractor.builder.reset";

    /** Component identifier for the {@code Close} footer button. */
    public static final @NotNull String ID_CLOSE = "extractor.builder.close";

    /**
     * Builds a page from the given builder state.
     *
     * @param state the builder state
     * @return the rendered page
     */
    public static @NotNull Page of(@NotNull PipelineBuilderState state) {
        Container.Builder container = Container.builder();
        container.withComponents(TextDisplay.of(titleLine(state)), Separator.small());

        DataPipeline pipeline = state.pipeline();
        for (int i = 0; i < pipeline.stages().size(); i++) {
            Stage<?, ?> stage = pipeline.stages().get(i);
            container.withComponents(StageSection.of(i, stage));
        }

        container.withComponents(Separator.small());
        container.withComponents(previewSection(state));
        container.withComponents(Separator.small());
        container.withComponents(footerRow());

        return Page.builder()
            .withComponents(container.build())
            .build();
    }

    private static @NotNull String titleLine(@NotNull PipelineBuilderState state) {
        String label = state.label().isEmpty() ? "(unsaved)" : state.label();
        StringBuilder out = new StringBuilder("# Pipeline Builder - ").append(label);
        if (!state.shortId().isEmpty())
            out.append(" `").append(state.shortId()).append("`");
        return out.toString();
    }

    private static @NotNull Section previewSection(@NotNull PipelineBuilderState state) {
        StringBuilder body = new StringBuilder();
        if (state.banner() != null && !state.banner().isEmpty())
            body.append("[!] ").append(state.banner()).append("\n");

        ValidationReport report = state.pipeline().validate();
        if (!report.isValid()) {
            body.append("**Validation:** ").append(report.issues().size()).append(" issue(s)\n");
            for (ValidationReport.Issue issue : report.issues()) {
                String prefix = issue.stageIndex() < 0 ? "pipeline" : "stage #" + issue.stageIndex();
                body.append("- ").append(prefix).append(": ").append(issue.message()).append("\n");
            }
        } else if (state.latestResult() != null) {
            body.append("**Result:** `").append(formatResult(state.latestResult())).append("`");
        } else {
            body.append("Ready to run.");
        }

        if (body.isEmpty()) body.append("(empty)");

        Button accessory = Button.builder()
            .withStyle(Button.Style.PRIMARY)
            .withLabel(state.latestResult() != null ? "Re-run" : "Run")
            .withIdentifier(ID_RUN)
            .build();

        return Section.builder()
            .withAccessory(accessory)
            .withComponents(TextDisplay.of(body.toString()))
            .build();
    }

    private static @NotNull ActionRow footerRow() {
        return ActionRow.of(
            Button.builder()
                .withStyle(Button.Style.PRIMARY)
                .withLabel("Add Stage")
                .withIdentifier(ID_ADD_STAGE)
                .build(),
            Button.builder()
                .withStyle(Button.Style.SUCCESS)
                .withLabel("Save as...")
                .withIdentifier(ID_SAVE)
                .build(),
            Button.builder()
                .withStyle(Button.Style.SECONDARY)
                .withLabel("Reset")
                .withIdentifier(ID_RESET)
                .build(),
            Button.builder()
                .withStyle(Button.Style.DANGER)
                .withLabel("Close")
                .withIdentifier(ID_CLOSE)
                .build()
        );
    }

    private static @NotNull String formatResult(@Nullable Object value) {
        if (value == null) return "null";
        String s = String.valueOf(value);
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }

}
