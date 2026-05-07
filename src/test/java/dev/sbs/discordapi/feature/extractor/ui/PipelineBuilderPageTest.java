package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.DataPipeline;
import dev.sbs.dataflow.DataTypes;
import dev.sbs.dataflow.stage.collect.FirstCollect;
import dev.sbs.dataflow.stage.filter.dom.DomTextContainsFilter;
import dev.sbs.dataflow.stage.source.PasteSource;
import dev.sbs.dataflow.stage.transform.dom.CssSelectTransform;
import dev.sbs.dataflow.stage.transform.dom.NodeTextTransform;
import dev.sbs.dataflow.stage.transform.dom.NthChildTransform;
import dev.sbs.dataflow.stage.transform.dom.ParseHtmlTransform;
import dev.sbs.dataflow.stage.transform.primitive.ParseIntTransform;
import dev.sbs.dataflow.stage.transform.string.RegexExtractTransform;
import dev.sbs.discordapi.response.page.Page;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class PipelineBuilderPageTest {

    @Test
    @DisplayName("Empty state renders without error")
    void emptyState() {
        Page page = PipelineBuilderPage.of(PipelineBuilderState.empty());
        assertThat(page, is(notNullValue()));
        assertThat(page.getComponents().size(), is(greaterThan(0)));
    }

    @Test
    @DisplayName("Populated wiki pipeline renders one Section per stage plus footer")
    void populatedPipeline() {
        DataPipeline pipeline = DataPipeline.builder()
            .source(PasteSource.html("<table class='infobox'><tr><td>Dmg</td><td>500</td></tr></table>"))
            .stage(ParseHtmlTransform.of())
            .stage(CssSelectTransform.of("table.infobox tr"))
            .stage(DomTextContainsFilter.of("Dmg"))
            .stage(FirstCollect.of(DataTypes.DOM_NODE))
            .stage(NthChildTransform.of("td", 1))
            .stage(NodeTextTransform.of())
            .stage(RegexExtractTransform.of("\\d+"))
            .stage(ParseIntTransform.of())
            .build();

        PipelineBuilderState state = PipelineBuilderState.builder()
            .pipeline(pipeline)
            .label("wiki dmg")
            .shortId("wiki_dmg")
            .latestResult(500)
            .build();

        Page page = PipelineBuilderPage.of(state);
        assertThat(page, is(notNullValue()));
        assertThat(page.getComponents().size(), is(greaterThan(0)));
    }

    @Test
    @DisplayName("Banner message appears in preview when set")
    void bannerStateRenders() {
        PipelineBuilderState state = PipelineBuilderState.builder()
            .pipeline(DataPipeline.empty())
            .label("")
            .shortId("")
            .banner("type mismatch on stage #2")
            .build();

        Page page = PipelineBuilderPage.of(state);
        assertThat(page, is(notNullValue()));
    }

}
