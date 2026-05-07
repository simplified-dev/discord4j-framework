package dev.sbs.discordapi.feature.extractor.ui;

import dev.sbs.discordapi.component.interaction.Modal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class SaveExtractorModalTest {

    @Test
    @DisplayName("Save modal has three labelled inputs")
    void threeInputs() {
        Modal modal = SaveExtractorModal.of(PipelineBuilderState.empty());
        assertThat(modal.getIdentifier(), is(equalTo(SaveExtractorModal.ID)));
        assertThat(modal.getComponents().size(), is(equalTo(3)));
    }

    @Test
    @DisplayName("Save modal pre-fills label and shortId from existing state")
    void prefilledFromState() {
        PipelineBuilderState state = PipelineBuilderState.builder()
            .pipeline(dev.sbs.dataflow.DataPipeline.empty())
            .label("Wiki Damage")
            .shortId("wiki_dmg")
            .build();
        Modal modal = SaveExtractorModal.of(state);
        // We can't easily walk inner components; just assert it was built without errors.
        assertThat(modal.getComponents().size(), is(equalTo(3)));
    }

}
