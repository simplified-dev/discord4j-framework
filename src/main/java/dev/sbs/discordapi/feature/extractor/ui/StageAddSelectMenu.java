package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.stage.StageCategory;
import dev.sbs.dataflow.stage.StageKind;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;

/**
 * Two-stage palette for the "Add Stage" flow: a top-level {@link SelectMenu.StringMenu} listing
 * {@link StageCategory categories}, and a per-category menu listing the {@link StageKind kinds}
 * within that category.
 * <p>
 * Discord caps a {@link SelectMenu.StringMenu} at 25 options. There are around 80 stage kinds
 * but each individual category fits comfortably under that limit, so the cascading picker
 * keeps the UI usable without resorting to followups. The categories menu has at most one
 * option per {@link StageCategory} value.
 * <p>
 * Both menus are returned without {@code onInteract} handlers - the caller wires those when
 * binding the menu inside a {@code Response}, closing over the live builder state.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class StageAddSelectMenu {

    /** Component id of the category-picker menu. */
    public static final @NotNull String ID_CATEGORY = "extractor.builder.add.category";

    /** Identifier prefix of a per-category kind picker; suffix is the {@link StageCategory} name. */
    public static final @NotNull String ID_KIND_PREFIX = "extractor.builder.add.kind.";

    /** Component id of the category picker scoped to a Branch sub-chain (BRANCH excluded). */
    public static final @NotNull String ID_CATEGORY_SUB_CHAIN = "extractor.builder.add.category.sub";

    /**
     * Builds the top-level category picker.
     *
     * @return the category select menu
     */
    public static @NotNull SelectMenu.StringMenu categories() {
        return categories(false);
    }

    /**
     * Builds the category picker scoped to a Branch sub-chain. The {@link StageCategory#BRANCH}
     * option is hidden so the v1 depth-1 cap is enforced at the UI surface.
     *
     * @return the sub-chain category select menu
     */
    public static @NotNull SelectMenu.StringMenu categoriesForSubChain() {
        return categories(true);
    }

    private static @NotNull SelectMenu.StringMenu categories(boolean inSubChain) {
        SelectMenu.StringMenu.Builder menu = SelectMenu.builder()
            .withIdentifier(inSubChain ? ID_CATEGORY_SUB_CHAIN : ID_CATEGORY)
            .withPlaceholder("Add stage - choose category");

        for (StageCategory category : StageCategory.values()) {
            if (StageKind.ofCategory(category).isEmpty()) continue;
            if (inSubChain && category == StageCategory.BRANCH) continue;
            menu.withOptions(SelectMenu.Option.builder()
                .withLabel(displayName(category))
                .withValue(category.name())
                .build());
        }

        return menu.build();
    }

    /**
     * Builds a kind picker scoped to the given category.
     *
     * @param category the category whose kinds should be listed
     * @return the kind select menu
     */
    public static @NotNull SelectMenu.StringMenu kindsIn(@NotNull StageCategory category) {
        SelectMenu.StringMenu.Builder menu = SelectMenu.builder()
            .withIdentifier(ID_KIND_PREFIX + category.name())
            .withPlaceholder("Add stage - choose " + displayName(category) + " kind");

        for (StageKind kind : StageKind.ofCategory(category)) {
            menu.withOptions(SelectMenu.Option.builder()
                .withLabel(kind.displayName())
                .withDescription(kind.description())
                .withValue(kind.name())
                .build());
        }

        return menu.build();
    }

    private static @NotNull String displayName(@NotNull StageCategory category) {
        String[] parts = category.name().toLowerCase().split("_");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return out.toString();
    }

}
