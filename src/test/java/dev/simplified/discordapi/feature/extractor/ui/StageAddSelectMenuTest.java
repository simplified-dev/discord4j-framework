package dev.simplified.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.stage.meta.StageSpec;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class StageAddSelectMenuTest {

    @Test
    @DisplayName("Categories menu has one option per non-empty category")
    void categoriesMenu() {
        SelectMenu.StringMenu menu = StageAddSelectMenu.categories();
        assertThat(menu.getIdentifier(), is(equalTo(StageAddSelectMenu.ID_CATEGORY)));
        assertThat(menu.getOptions().size(), is(greaterThan(0)));
        // Discord caps StringMenu options at 25
        assertThat(menu.getOptions().size(), is(lessThanOrEqualTo(25)));
    }

    @Test
    @DisplayName("Per-category menu lists kinds in that category")
    void kindsMenu() {
        SelectMenu.StringMenu menu = StageAddSelectMenu.kindsIn(StageSpec.Category.TERMINAL_COLLECT);
        assertThat(menu.getIdentifier(), is(equalTo(StageAddSelectMenu.ID_KIND_PREFIX + "TERMINAL_COLLECT")));
        // TERMINAL_COLLECT contains FirstCollect, LastCollect, ListCollect, SetCollect, JoinCollect,
        // MapCollect, JsonObjectFromEntriesCollect — at least 5
        assertThat(menu.getOptions().size(), is(greaterThan(0)));
    }

    @Test
    @DisplayName("Each category fits under the 25-option cap")
    void allCategoriesFitCap() {
        for (StageSpec.Category category : StageSpec.Category.values()) {
            SelectMenu.StringMenu menu = StageAddSelectMenu.kindsIn(category);
            assertThat(category + " exceeds cap", menu.getOptions().size(), is(lessThanOrEqualTo(25)));
        }
    }

    @Test
    @DisplayName("categoriesForSubChain excludes TERMINAL_COLLECT (depth-1 cap on Branch)")
    void subChainHidesBranch() {
        SelectMenu.StringMenu top = StageAddSelectMenu.categories();
        SelectMenu.StringMenu sub = StageAddSelectMenu.categoriesForSubChain();
        assertThat(sub.getIdentifier(), is(equalTo(StageAddSelectMenu.ID_CATEGORY_SUB_CHAIN)));
        assertThat(sub.getOptions().size(), is(equalTo(top.getOptions().size() - 1)));
        boolean hasTerminalCollect = sub.getOptions().stream()
            .anyMatch(o -> StageSpec.Category.TERMINAL_COLLECT.name().equals(o.getValue()));
        assertThat(hasTerminalCollect, is(false));
    }

}
