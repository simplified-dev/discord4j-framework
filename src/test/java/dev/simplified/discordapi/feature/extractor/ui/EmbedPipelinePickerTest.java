package dev.simplified.discordapi.feature.extractor.ui;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.dataflow.stage.source.LiteralSource;
import dev.simplified.dataflow.stage.transform.json.JsonAsIntTransform;
import dev.simplified.dataflow.stage.transform.json.ParseJsonTransform;
import dev.simplified.discordapi.component.interaction.SelectMenu;
import dev.simplified.discordapi.feature.extractor.Extractor;
import dev.simplified.discordapi.feature.extractor.InMemoryExtractorStore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class EmbedPipelinePickerTest {

    @Test
    @DisplayName("Picker emits a menu listing visible extractors")
    void listsVisible() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = newRow(100L, "wiki_dmg", "Wiki Damage");
        store.save(row).block();

        SelectMenu.StringMenu menu = EmbedPipelinePicker.of(store, 100L, null).block();
        assertThat(menu, is(notNullValue()));
        assertThat(menu.getIdentifier(), is(equalTo(EmbedPipelinePicker.ID)));
        assertThat(menu.getOptions().size(), is(equalTo(1)));
        assertThat(menu.getOptions().getFirst().getValue(), is(equalTo(row.getId().toString())));
    }

    @Test
    @DisplayName("Picker emits empty when no extractors are visible")
    void emptyWhenNothingVisible() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        SelectMenu.StringMenu menu = EmbedPipelinePicker.of(store, 100L, null).block();
        assertThat(menu, is(nullValue()));
    }

    @Test
    @DisplayName("Picker caps at OPTION_CAP options")
    void capsAtCap() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        for (int i = 0; i < EmbedPipelinePicker.OPTION_CAP + 5; i++)
            store.save(newRow(100L, "x" + i, "label-" + i)).block();
        SelectMenu.StringMenu menu = EmbedPipelinePicker.of(store, 100L, null).block();
        assertThat(menu.getOptions().size(), is(lessThanOrEqualTo(EmbedPipelinePicker.OPTION_CAP)));
    }

    private static Extractor newRow(long ownerId, String shortId, String label) {
        Extractor row = new Extractor();
        row.setOwnerUserId(ownerId);
        row.setShortId(shortId);
        row.setLabel(label);
        row.setVisibility(Extractor.Visibility.PRIVATE);
        row.setPipeline(DataPipeline.builder()
            .source(LiteralSource.rawJson("0"))
            .stage(ParseJsonTransform.of())
            .stage(JsonAsIntTransform.of())
            .build());
        return row;
    }

}
