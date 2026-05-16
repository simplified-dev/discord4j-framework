package dev.simplified.discordapi.feature.extractor.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

class ExtractorResultFormatterTest {

    @Test
    @DisplayName("null formats as 'null'")
    void nullValue() {
        assertThat(ExtractorResultFormatter.format(null), is(equalTo("null")));
    }

    @Test
    @DisplayName("Scalar formats as toString")
    void scalar() {
        assertThat(ExtractorResultFormatter.format(500), is(equalTo("500")));
        assertThat(ExtractorResultFormatter.format("hello"), is(equalTo("hello")));
    }

    @Test
    @DisplayName("List renders as bulleted lines")
    void list() {
        String out = ExtractorResultFormatter.format(List.of("a", "b", "c"));
        assertThat(out, is(equalTo("- a\n- b\n- c")));
    }

    @Test
    @DisplayName("Empty list renders as []")
    void emptyList() {
        assertThat(ExtractorResultFormatter.format(List.of()), is(equalTo("[]")));
    }

    @Test
    @DisplayName("Map renders as key: value pairs")
    void map() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("dmg", 500);
        m.put("strength", 220);
        String out = ExtractorResultFormatter.format(m);
        assertThat(out, containsString("**dmg**: 500"));
        assertThat(out, containsString("**strength**: 220"));
    }

    @Test
    @DisplayName("Empty map renders as {}")
    void emptyMap() {
        assertThat(ExtractorResultFormatter.format(Map.of()), is(equalTo("{}")));
    }

    @Test
    @DisplayName("Output exceeding the limit gets truncated with ellipsis")
    void truncates() {
        String huge = "x".repeat(2000);
        String out = ExtractorResultFormatter.format(huge);
        assertThat(out.length(), is(lessThanOrEqualTo(ExtractorResultFormatter.DEFAULT_LIMIT)));
        assertThat(out, org.hamcrest.Matchers.endsWith("..."));
    }

    @Test
    @DisplayName("Lists past MAX_ENTRIES summarise the remainder")
    void manyEntries() {
        java.util.List<Integer> many = new java.util.ArrayList<>();
        for (int i = 0; i < 50; i++) many.add(i);
        String out = ExtractorResultFormatter.format(many);
        assertThat(out, containsString("more"));
    }

    @Test
    @DisplayName("Nested map -> list renders both levels")
    void nested() {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("group", List.of("a", "b"));
        String out = ExtractorResultFormatter.format(m);
        assertThat(out, startsWith("**group**:"));
        assertThat(out, containsString("- a"));
        assertThat(out, containsString("- b"));
    }

}
