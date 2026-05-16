package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.dataflow.DataTypes;
import dev.simplified.dataflow.PipelineContext;
import dev.simplified.dataflow.stage.filter.numeric.IntGreaterThanFilter;
import dev.simplified.dataflow.stage.source.EmbedSource;
import dev.simplified.dataflow.stage.source.LiteralSource;
import dev.simplified.dataflow.stage.source.UrlSource;
import dev.simplified.dataflow.stage.terminal.collect.MapCollect;
import dev.simplified.dataflow.stage.transform.dom.DomOuterHtmlTransform;
import dev.simplified.dataflow.stage.transform.dom.DomTextTransform;
import dev.simplified.dataflow.stage.transform.dom.ParseHtmlTransform;
import dev.simplified.dataflow.stage.transform.json.JsonAsIntTransform;
import dev.simplified.dataflow.stage.transform.json.ParseJsonTransform;
import dev.simplified.discordapi.feature.extractor.Extractor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class PipelineBuilderSessionTest {

    @Test
    @DisplayName("startNew yields an empty pipeline state")
    void startNew() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        assertThat(session.state().pipeline().stages().isEmpty(), is(true));
        assertThat(session.state().banner(), is(nullValue()));
    }

    @Test
    @DisplayName("appendStage adds a source on an empty pipeline")
    void appendSource() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of(
            "value", "<html/>",
            "outputType", "RAW_HTML"
        ));
        assertThat(session.state().pipeline().stages().size(), is(equalTo(1)));
        assertThat(session.state().banner(), is(nullValue()));
    }

    @Test
    @DisplayName("appendStage chains multiple stages")
    void appendChain() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p>x</p>", "outputType", "RAW_HTML"));
        session.appendStage(ParseHtmlTransform.class, Map.of());
        session.appendStage(DomTextTransform.class, Map.of());
        assertThat(session.state().pipeline().stages().size(), is(equalTo(3)));
    }

    @Test
    @DisplayName("appendStage with bad input sets banner without mutating the pipeline")
    void appendStageBadInput() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(UrlSource.class, Map.of("url", "x", "outputType", "RAW_HTML"));
        int sizeBefore = session.state().pipeline().stages().size();
        // INT field with non-numeric input
        session.appendStage(IntGreaterThanFilter.class, Map.of("threshold", "abc"));
        assertThat(session.state().pipeline().stages().size(), is(equalTo(sizeBefore)));
        assertThat(session.state().banner(), containsString("Invalid number"));
    }

    @Test
    @DisplayName("replaceStage swaps the stage at the given index")
    void replaceStage() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p>x</p>", "outputType", "RAW_HTML"));
        session.appendStage(ParseHtmlTransform.class, Map.of());
        session.replaceStage(1, ParseHtmlTransform.class, Map.of());
        assertThat(session.state().pipeline().stages().get(1).kindId(), is(equalTo("PARSE_HTML")));
    }

    @Test
    @DisplayName("replaceStage at out-of-range index sets banner")
    void replaceOutOfRange() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.replaceStage(99, ParseHtmlTransform.class, Map.of());
        assertThat(session.state().banner(), containsString("No such stage"));
    }

    @Test
    @DisplayName("removeStage trims the pipeline")
    void removeStage() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p>x</p>", "outputType", "RAW_HTML"));
        session.appendStage(ParseHtmlTransform.class, Map.of());
        session.removeStage(1);
        assertThat(session.state().pipeline().stages().size(), is(equalTo(1)));
    }

    @Test
    @DisplayName("reset clears the pipeline but preserves label and shortId")
    void reset() {
        PipelineBuilderSession session = PipelineBuilderSession.resume(PipelineBuilderState.builder()
            .pipeline(DataPipeline.builder()
                .source(LiteralSource.rawHtml("<p/>"))
                .stage(ParseHtmlTransform.of())
                .build())
            .label("wiki dmg")
            .shortId("wiki_dmg")
            .build());
        session.reset();
        assertThat(session.state().pipeline().stages().isEmpty(), is(true));
        assertThat(session.state().label(), is(equalTo("wiki dmg")));
        assertThat(session.state().shortId(), is(equalTo("wiki_dmg")));
    }

    @Test
    @DisplayName("recordRunResult stores the result and clears any banner")
    void recordRunResult() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.banner("noisy banner");
        session.recordRunResult(500);
        assertThat(session.state().latestResult(), is(equalTo(500)));
        assertThat(session.state().banner(), is(nullValue()));
    }

    @Test
    @DisplayName("Mutating the pipeline clears the latestResult")
    void mutationClearsResult() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p/>", "outputType", "RAW_HTML"));
        session.recordRunResult(42);
        assertThat(session.state().latestResult(), is(notNullValue()));
        session.appendStage(ParseHtmlTransform.class, Map.of());
        assertThat(session.state().latestResult(), is(nullValue()));
    }

    @Test
    @DisplayName("runPipeline on a valid pipeline records the result")
    void runValidPipeline() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p>hello</p>", "outputType", "RAW_HTML"));
        session.appendStage(ParseHtmlTransform.class, Map.of());
        session.appendStage(DomTextTransform.class, Map.of());
        session.runPipeline(PipelineContext.defaults());
        assertThat(session.state().latestResult(), is(equalTo("hello")));
        assertThat(session.state().banner(), is(nullValue()));
    }

    @Test
    @DisplayName("runPipeline on an invalid pipeline writes to the banner without executing")
    void runInvalidPipeline() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        // No source -> validation rejects
        session.runPipeline(PipelineContext.defaults());
        assertThat(session.state().banner(), containsString("validation"));
        assertThat(session.state().latestResult(), is(nullValue()));
    }

    @Test
    @DisplayName("validateSaveInputs accepts a complete, well-formed submission")
    void validSave() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p>x</p>", "outputType", "RAW_HTML"));
        session.appendStage(ParseHtmlTransform.class, Map.of());
        session.appendStage(DomTextTransform.class, Map.of());
        PipelineBuilderSession.SaveValidation v = session.validateSaveInputs(Map.of(
            SaveExtractorModal.FIELD_LABEL, "Wiki Damage",
            SaveExtractorModal.FIELD_SHORT_ID, "wiki_dmg",
            SaveExtractorModal.FIELD_VISIBILITY, "PRIVATE"
        ));
        assertThat(v.ok(), is(true));
        assertThat(v.label(), is(equalTo("Wiki Damage")));
        assertThat(v.shortId(), is(equalTo("wiki_dmg")));
        assertThat(v.visibility(), is(equalTo(Extractor.Visibility.PRIVATE)));
    }

    @Test
    @DisplayName("validateSaveInputs rejects empty pipelines before checking other inputs")
    void rejectsEmptyPipeline() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        PipelineBuilderSession.SaveValidation v = session.validateSaveInputs(Map.of(
            SaveExtractorModal.FIELD_LABEL, "x",
            SaveExtractorModal.FIELD_SHORT_ID, "x",
            SaveExtractorModal.FIELD_VISIBILITY, "PRIVATE"
        ));
        assertThat(v.ok(), is(false));
        assertThat(v.error(), containsString("empty"));
    }

    @Test
    @DisplayName("validateSaveInputs lowercases shortId and uppercases visibility")
    void normalisesCase() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p/>", "outputType", "RAW_HTML"));
        PipelineBuilderSession.SaveValidation v = session.validateSaveInputs(Map.of(
            SaveExtractorModal.FIELD_LABEL, "X",
            SaveExtractorModal.FIELD_SHORT_ID, "Wiki_Dmg",
            SaveExtractorModal.FIELD_VISIBILITY, "public"
        ));
        assertThat(v.ok(), is(true));
        assertThat(v.shortId(), is(equalTo("wiki_dmg")));
        assertThat(v.visibility(), is(equalTo(Extractor.Visibility.PUBLIC)));
    }

    @Test
    @DisplayName("validateSaveInputs rejects shortId with disallowed characters")
    void shortIdRegex() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p/>", "outputType", "RAW_HTML"));
        PipelineBuilderSession.SaveValidation v = session.validateSaveInputs(Map.of(
            SaveExtractorModal.FIELD_LABEL, "x",
            SaveExtractorModal.FIELD_SHORT_ID, "Wiki Dmg!",
            SaveExtractorModal.FIELD_VISIBILITY, "PRIVATE"
        ));
        assertThat(v.ok(), is(false));
        assertThat(v.error(), containsString("Short id"));
    }

    @Test
    @DisplayName("validateSaveInputs rejects unknown visibility values")
    void unknownVisibility() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        session.appendStage(LiteralSource.class, Map.of("value", "<p/>", "outputType", "RAW_HTML"));
        PipelineBuilderSession.SaveValidation v = session.validateSaveInputs(Map.of(
            SaveExtractorModal.FIELD_LABEL, "x",
            SaveExtractorModal.FIELD_SHORT_ID, "x",
            SaveExtractorModal.FIELD_VISIBILITY, "TEAM"
        ));
        assertThat(v.ok(), is(false));
        assertThat(v.error(), containsString("Visibility"));
    }

    /* ============================  branch sub-chain  ============================ */

    private static PipelineBuilderSession sessionWithBranch() {
        // Build a session whose pipeline ends in a real (empty) MapCollect (the new home of
        // the old Branch concept). MapCollect cannot be constructed via the modal cascade
        // because its NamedChains config is sub-pipeline-only, so we drop one in directly.
        MapCollect<?> branch = MapCollect.over(DataTypes.DOM_NODE).build();
        DataPipeline p = DataPipeline.builder()
            .source(LiteralSource.rawHtml("<p/>"))
            .stage(ParseHtmlTransform.of())
            .stage(branch)
            .build();
        return PipelineBuilderSession.resume(PipelineBuilderState.builder()
            .pipeline(p).label("").shortId("").build());
    }

    @Test
    @DisplayName("addBranchOutput creates a fresh empty named output")
    void addBranchOutputAdds() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(2, "dmg");
        MapCollect<?> branch = (MapCollect<?>) session.state().pipeline().stages().get(2);
        assertThat(branch.outputs().chains().keySet(), org.hamcrest.Matchers.contains("dmg"));
        assertThat(branch.outputs().chains().get("dmg").size(), is(equalTo(0)));
    }

    @Test
    @DisplayName("addBranchOutput rejects duplicates")
    void addBranchOutputDuplicate() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(2, "dmg");
        session.addBranchOutput(2, "dmg");
        assertThat(session.state().banner(), containsString("already exists"));
    }

    @Test
    @DisplayName("appendBranchStage appends to the named sub-chain")
    void appendIntoSubChain() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(2, "dmg");
        session.appendBranchStage(2, "dmg", DomTextTransform.class, Map.of());
        MapCollect<?> branch = (MapCollect<?>) session.state().pipeline().stages().get(2);
        assertThat(branch.outputs().chains().get("dmg").size(), is(equalTo(1)));
        assertThat(branch.outputs().chains().get("dmg").stages().getFirst().kindId(), is(equalTo("TRANSFORM_NODE_TEXT")));
    }

    @Test
    @DisplayName("appendBranchStage rejects nested BRANCH (depth-1 cap)")
    void rejectsNestedBranch() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(2, "dmg");
        session.appendBranchStage(2, "dmg", MapCollect.class, Map.of());
        assertThat(session.state().banner(), containsString("cannot be nested"));
        MapCollect<?> branch = (MapCollect<?>) session.state().pipeline().stages().get(2);
        assertThat(branch.outputs().chains().get("dmg").isEmpty(), is(true));
    }

    @Test
    @DisplayName("replaceBranchStage swaps a sub-chain stage")
    void replaceInSubChain() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(2, "dmg");
        session.appendBranchStage(2, "dmg", DomTextTransform.class, Map.of());
        session.replaceBranchStage(2, "dmg", 0, DomOuterHtmlTransform.class, Map.of());
        MapCollect<?> branch = (MapCollect<?>) session.state().pipeline().stages().get(2);
        assertThat(branch.outputs().chains().get("dmg").stages().getFirst().kindId(), is(equalTo("TRANSFORM_DOM_OUTER_HTML")));
    }

    @Test
    @DisplayName("removeBranchStage drops a sub-chain stage")
    void removeFromSubChain() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(2, "dmg");
        session.appendBranchStage(2, "dmg", DomTextTransform.class, Map.of());
        session.removeBranchStage(2, "dmg", 0);
        MapCollect<?> branch = (MapCollect<?>) session.state().pipeline().stages().get(2);
        assertThat(branch.outputs().chains().get("dmg").isEmpty(), is(true));
    }

    @Test
    @DisplayName("removeBranchOutput drops a named sub-chain")
    void removeOutput() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(2, "dmg");
        session.removeBranchOutput(2, "dmg");
        MapCollect<?> branch = (MapCollect<?>) session.state().pipeline().stages().get(2);
        assertThat(branch.outputs().chains().isEmpty(), is(true));
    }

    @Test
    @DisplayName("Branch mutators reject non-Branch indices")
    void nonBranchIndex() {
        PipelineBuilderSession session = sessionWithBranch();
        session.addBranchOutput(0, "dmg"); // index 0 is the source, not a branch
        assertThat(session.state().banner(), containsString("not a Branch"));
    }

    /* ============================  pipeline embed  ============================ */

    @Test
    @DisplayName("appendEmbedStage appends an EmbedSource carrying the extractor's UUID")
    void appendEmbed() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        Extractor saved = new Extractor();
        saved.setId(java.util.UUID.randomUUID());
        saved.setLabel("inner");
        saved.setShortId("inner");
        saved.setPipeline(DataPipeline.builder()
            .source(LiteralSource.rawJson("42"))
            .stage(ParseJsonTransform.of())
            .stage(JsonAsIntTransform.of())
            .build());
        session.appendEmbedStage(saved);
        assertThat(session.state().pipeline().stages().size(), is(equalTo(1)));
        assertThat(session.state().pipeline().stages().getFirst(), is(org.hamcrest.Matchers.instanceOf(EmbedSource.class)));
    }

    @Test
    @DisplayName("appendEmbedStage rejects extractors with empty pipelines")
    void embedRejectsEmpty() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        Extractor empty = new Extractor();
        empty.setId(java.util.UUID.randomUUID());
        // default empty pipeline
        session.appendEmbedStage(empty);
        assertThat(session.state().banner(), containsString("empty extractor"));
    }

    @Test
    @DisplayName("appendEmbedStage rejects extractors whose definition cannot be parsed")
    void embedRejectsBrokenDefinition() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        Extractor broken = new Extractor();
        broken.setId(java.util.UUID.randomUUID());
        broken.setDefinitionJson("[{kind:\"NOT_REAL\"}]");
        session.appendEmbedStage(broken);
        assertThat(session.state().banner(), containsString("unparseable"));
    }

    @Test
    @DisplayName("recordSaved updates state with the persisted row's identity")
    void recordSavedUpdatesState() {
        PipelineBuilderSession session = PipelineBuilderSession.startNew();
        Extractor saved = new Extractor();
        saved.setId(java.util.UUID.randomUUID());
        saved.setLabel("Wiki Damage");
        saved.setShortId("wiki_dmg");
        session.recordSaved(saved);
        assertThat(session.state().label(), is(equalTo("Wiki Damage")));
        assertThat(session.state().shortId(), is(equalTo("wiki_dmg")));
        assertThat(session.state().backingRow(), is(equalTo(saved)));
        assertThat(session.state().banner(), containsString("Saved as"));
    }

}
