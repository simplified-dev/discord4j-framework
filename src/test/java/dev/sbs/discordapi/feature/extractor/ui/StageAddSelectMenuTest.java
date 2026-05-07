package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.stage.StageCategory;
import dev.sbs.discordapi.component.interaction.SelectMenu;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;

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
        SelectMenu.StringMenu menu = StageAddSelectMenu.kindsIn(StageCategory.COLLECT);
        assertThat(menu.getIdentifier(), is(equalTo(StageAddSelectMenu.ID_KIND_PREFIX + "COLLECT")));
        // COLLECT has 5 kinds: FIRST, LAST, LIST, SET, JOIN
        assertThat(menu.getOptions().size(), is(equalTo(5)));
    }

    @Test
    @DisplayName("Each category fits under the 25-option cap")
    void allCategoriesFitCap() {
        for (StageCategory category : StageCategory.values()) {
            SelectMenu.StringMenu menu = StageAddSelectMenu.kindsIn(category);
            assertThat(category + " exceeds cap", menu.getOptions().size(), is(lessThanOrEqualTo(25)));
        }
    }

    @Test
    @DisplayName("categoriesForSubChain excludes BRANCH (depth-1 cap)")
    void subChainHidesBranch() {
        SelectMenu.StringMenu top = StageAddSelectMenu.categories();
        SelectMenu.StringMenu sub = StageAddSelectMenu.categoriesForSubChain();
        assertThat(sub.getIdentifier(), is(equalTo(StageAddSelectMenu.ID_CATEGORY_SUB_CHAIN)));
        assertThat(sub.getOptions().size(), is(equalTo(top.getOptions().size() - 1)));
        boolean hasBranch = sub.getOptions().stream().anyMatch(o -> "BRANCH".equals(o.getValue()));
        assertThat(hasBranch, is(false));
    }

}
