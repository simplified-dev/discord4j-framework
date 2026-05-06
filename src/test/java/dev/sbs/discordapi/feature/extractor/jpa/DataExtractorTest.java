package dev.sbs.discordapi.feature.extractor.jpa;

import dev.sbs.dataflow.DataPipeline;
import dev.sbs.dataflow.PipelineContext;
import dev.sbs.dataflow.stage.source.PasteSource;
import dev.sbs.dataflow.stage.transform.json.JsonAsIntTransform;
import dev.sbs.dataflow.stage.transform.json.ParseJsonTransform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

/** Unit tests for {@link DataExtractor}. Repository tests live alongside JPA integration. */
class DataExtractorTest {

    @Test
    @DisplayName("PRIVATE visibility lets only the owner use the extractor")
    void privateVisibility() {
        DataExtractor row = newExtractor(100L, DataExtractor.Visibility.PRIVATE, null);
        assertThat(row.canBeUsedBy(100L, null), is(true));
        assertThat(row.canBeUsedBy(100L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, null), is(false));
        assertThat(row.canBeUsedBy(200L, 999L), is(false));
    }

    @Test
    @DisplayName("GUILD visibility lets anyone in the saver's guild use the extractor")
    void guildVisibility() {
        DataExtractor row = newExtractor(100L, DataExtractor.Visibility.GUILD, 999L);
        assertThat(row.canBeUsedBy(100L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, 888L), is(false));
        assertThat(row.canBeUsedBy(200L, null), is(false));
    }

    @Test
    @DisplayName("PUBLIC visibility lets anyone use the extractor")
    void publicVisibility() {
        DataExtractor row = newExtractor(100L, DataExtractor.Visibility.PUBLIC, null);
        assertThat(row.canBeUsedBy(100L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, null), is(true));
        assertThat(row.canBeUsedBy(300L, 888L), is(true));
    }

    @Test
    @DisplayName("pipeline()/setPipeline() round-trip the dataflow definition")
    void pipelineRoundTrip() {
        DataPipeline original = DataPipeline.builder()
            .source(PasteSource.json("42"))
            .stage(ParseJsonTransform.create())
            .stage(JsonAsIntTransform.create())
            .build();

        DataExtractor row = new DataExtractor();
        row.setPipeline(original);

        Integer result = row.pipeline().execute(PipelineContext.empty());
        assertThat(result, is(equalTo(42)));
    }

    @Test
    @DisplayName("Default DataExtractor has an empty-array definition_json")
    void defaultDefinitionIsEmpty() {
        DataExtractor row = new DataExtractor();
        assertThat(row.getDefinitionJson(), is(equalTo("[]")));
        assertThat(row.getVisibility(), is(equalTo(DataExtractor.Visibility.PRIVATE)));
    }

    private static DataExtractor newExtractor(long ownerId, DataExtractor.Visibility v, Long guildId) {
        DataExtractor row = new DataExtractor();
        row.setId(UUID.randomUUID());
        row.setOwnerUserId(ownerId);
        row.setName("test");
        row.setVisibility(v);
        row.setGuildId(guildId);
        return row;
    }

}
