package dev.sbs.discordapi.feature.extractor;

import dev.sbs.dataflow.DataPipeline;
import dev.sbs.dataflow.PipelineContext;
import dev.sbs.dataflow.stage.source.LiteralSource;
import dev.sbs.dataflow.stage.transform.dom.NodeTextTransform;
import dev.sbs.dataflow.stage.transform.dom.ParseHtmlTransform;
import dev.sbs.dataflow.stage.transform.json.JsonAsIntTransform;
import dev.sbs.dataflow.stage.transform.json.ParseJsonTransform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class ExtractorRunnerTest {

    @Test
    @DisplayName("run executes a saved pipeline and returns the result")
    void runSuccess() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = new Extractor();
        row.setOwnerUserId(100L);
        row.setShortId("dmg");
        row.setLabel("Damage");
        row.setVisibility(Extractor.Visibility.PRIVATE);
        row.setPipeline(DataPipeline.builder()
            .source(LiteralSource.rawJson("42"))
            .stage(ParseJsonTransform.of())
            .stage(JsonAsIntTransform.of())
            .build());
        store.save(row).block();

        ExtractorRunner.Result result = ExtractorRunner.run(
            store, "dmg", 100L, null, e -> PipelineContext.defaults()
        ).block();
        assertThat(result.ok(), is(true));
        assertThat(result.value(), is(equalTo(42)));
        assertThat(result.extractor(), is(notNullValue()));
    }

    @Test
    @DisplayName("run returns a not-found error when no visible extractor matches")
    void runNotFound() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        ExtractorRunner.Result result = ExtractorRunner.run(
            store, "missing", 100L, null, e -> PipelineContext.defaults()
        ).block();
        assertThat(result.ok(), is(false));
        assertThat(result.error(), containsString("No extractor named"));
    }

    @Test
    @DisplayName("run respects visibility - private extractors are invisible to others")
    void runVisibilityScoped() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = new Extractor();
        row.setOwnerUserId(100L);
        row.setShortId("dmg");
        row.setVisibility(Extractor.Visibility.PRIVATE);
        row.setPipeline(DataPipeline.builder().source(LiteralSource.text("x")).build());
        store.save(row).block();

        ExtractorRunner.Result mine = ExtractorRunner.run(store, "dmg", 100L, null, e -> PipelineContext.defaults()).block();
        assertThat(mine.ok(), is(true));
        ExtractorRunner.Result theirs = ExtractorRunner.run(store, "dmg", 200L, null, e -> PipelineContext.defaults()).block();
        assertThat(theirs.ok(), is(false));
    }

    @Test
    @DisplayName("run wraps runtime exceptions into a banner-friendly error")
    void runFailure() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = new Extractor();
        row.setOwnerUserId(100L);
        row.setShortId("broken");
        row.setVisibility(Extractor.Visibility.PRIVATE);
        // Set a malformed JSON definition - pipeline() will throw on parse
        row.setDefinitionJson("[{kind:\"NOT_REAL\"}]");
        store.save(row).block();

        ExtractorRunner.Result result = ExtractorRunner.run(
            store, "broken", 100L, null, e -> PipelineContext.defaults()
        ).block();
        assertThat(result.ok(), is(false));
        assertThat(result.error(), is(notNullValue()));
    }

    @Test
    @DisplayName("formatted produces the result string for success and the error for failure")
    void formatted() {
        ExtractorRunner.Result success = new ExtractorRunner.Result(new Extractor(), "hello", null);
        assertThat(success.formatted(), is(equalTo("hello")));
        ExtractorRunner.Result failure = new ExtractorRunner.Result(null, null, "oh no");
        assertThat(failure.formatted(), is(equalTo("oh no")));
    }

    @Test
    @DisplayName("contextFactory is invoked once per run and receives the resolved extractor")
    void contextFactoryReceivesExtractor() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = new Extractor();
        row.setOwnerUserId(100L);
        row.setShortId("html");
        row.setLabel("HTML");
        row.setVisibility(Extractor.Visibility.PRIVATE);
        row.setPipeline(DataPipeline.builder()
            .source(LiteralSource.rawHtml("<p>hi</p>"))
            .stage(ParseHtmlTransform.of())
            .stage(NodeTextTransform.of())
            .build());
        store.save(row).block();

        java.util.concurrent.atomic.AtomicReference<Extractor> seen = new java.util.concurrent.atomic.AtomicReference<>();
        ExtractorRunner.run(store, "html", 100L, null, e -> {
            seen.set(e);
            return PipelineContext.defaults();
        }).block();
        assertThat(seen.get(), is(notNullValue()));
        assertThat(seen.get().getShortId(), is(equalTo("html")));
    }

}
