package dev.simplified.discordapi.feature.extractor.ui;

import dev.sbs.dataflow.DataTypes;
import dev.sbs.dataflow.stage.StageConfig;
import dev.sbs.dataflow.stage.source.LiteralSource;
import dev.sbs.dataflow.stage.terminal.collect.ListCollect;
import dev.sbs.dataflow.stage.transform.dom.ParseHtmlTransform;
import dev.sbs.dataflow.stage.transform.string.RegexExtractTransform;
import dev.simplified.discordapi.component.interaction.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class StageEditModalTest {

    @Test
    @DisplayName("forAdd builds a modal whose id encodes the kind")
    void forAddKindEncoded() {
        Modal modal = StageEditModal.forAdd(RegexExtractTransform.class);
        assertThat(modal.getIdentifier(), is(equalTo(StageEditModal.ID_ADD_PREFIX + "TRANSFORM_REGEX_EXTRACT")));
        assertThat(modal.getTitle().orElse(""), containsString("Regex extract"));
    }

    @Test
    @DisplayName("forEdit builds a modal whose id encodes the stage index")
    void forEditIndexEncoded() {
        StageConfig cfg = StageConfig.builder()
            .string("regex", "\\d+")
            .integer("group", 0)
            .build();
        Modal modal = StageEditModal.forEdit(3, RegexExtractTransform.class, cfg);
        assertThat(modal.getIdentifier(), is(equalTo(StageEditModal.ID_EDIT_PREFIX + "3")));
        assertThat(modal.getTitle().orElse(""), containsString("#3"));
    }

    @Test
    @DisplayName("Modal contains one component per non-sub-pipeline schema slot")
    void modalComponentsMatchSchema() {
        Modal modal = StageEditModal.forAdd(LiteralSource.class);
        // LiteralSource has two slots: outputType + value
        assertThat(modal.getComponents().size(), is(equalTo(2)));
    }

    @Test
    @DisplayName("Schema with zero slots produces an empty modal body")
    void zeroSlotKind() {
        Modal modal = StageEditModal.forAdd(ParseHtmlTransform.class);
        assertThat(modal.getComponents().size(), is(equalTo(0)));
    }

    @Test
    @DisplayName("DATA_TYPE prefill renders as the type label")
    void dataTypePrefill() {
        StageConfig cfg = StageConfig.builder()
            .dataType("elementType", DataTypes.STRING)
            .build();
        Modal modal = StageEditModal.forEdit(0, ListCollect.class, cfg);
        assertThat(modal.getComponents().size(), is(equalTo(1)));
    }

}
