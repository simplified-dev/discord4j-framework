package dev.simplified.discordapi.feature.extractor;

import dev.simplified.dataflow.DataPipeline;
import dev.simplified.dataflow.PipelineContext;
import dev.simplified.dataflow.stage.source.LiteralSource;
import dev.simplified.dataflow.stage.transform.json.JsonAsIntTransform;
import dev.simplified.dataflow.stage.transform.json.ParseJsonTransform;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class ExtractorTest {

    @Test
    @DisplayName("PRIVATE visibility lets only the owner use the extractor")
    void privateVisibility() {
        Extractor row = newExtractor(100L, Extractor.Visibility.PRIVATE, null);
        assertThat(row.canBeUsedBy(100L, null), is(true));
        assertThat(row.canBeUsedBy(100L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, null), is(false));
        assertThat(row.canBeUsedBy(200L, 999L), is(false));
    }

    @Test
    @DisplayName("GUILD visibility lets anyone in the saver's guild use the extractor")
    void guildVisibility() {
        Extractor row = newExtractor(100L, Extractor.Visibility.GUILD, 999L);
        assertThat(row.canBeUsedBy(100L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, 888L), is(false));
        assertThat(row.canBeUsedBy(200L, null), is(false));
    }

    @Test
    @DisplayName("PUBLIC visibility lets anyone use the extractor")
    void publicVisibility() {
        Extractor row = newExtractor(100L, Extractor.Visibility.PUBLIC, null);
        assertThat(row.canBeUsedBy(100L, 999L), is(true));
        assertThat(row.canBeUsedBy(200L, null), is(true));
        assertThat(row.canBeUsedBy(300L, 888L), is(true));
    }

    @Test
    @DisplayName("pipeline()/setPipeline() round-trip the dataflow definition")
    void pipelineRoundTrip() {
        DataPipeline original = DataPipeline.builder()
            .source(LiteralSource.rawJson("42"))
            .stage(ParseJsonTransform.of())
            .stage(JsonAsIntTransform.of())
            .build();

        Extractor row = new Extractor();
        row.setPipeline(original);

        Integer result = row.pipeline().execute(PipelineContext.defaults());
        assertThat(result, is(equalTo(42)));
    }

    @Test
    @DisplayName("Default Extractor has an empty-array definitionJson")
    void defaultDefinitionIsEmpty() {
        Extractor row = new Extractor();
        assertThat(row.getDefinitionJson(), is(equalTo("[]")));
        assertThat(row.getVisibility(), is(equalTo(Extractor.Visibility.PRIVATE)));
    }

    private static Extractor newExtractor(long ownerId, Extractor.Visibility v, Long guildId) {
        Extractor row = new Extractor();
        row.setId(UUID.randomUUID());
        row.setOwnerUserId(ownerId);
        row.setShortId("test");
        row.setLabel("Test");
        row.setVisibility(v);
        row.setGuildId(guildId);
        return row;
    }

}
