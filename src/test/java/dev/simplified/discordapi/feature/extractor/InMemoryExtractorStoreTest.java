package dev.simplified.discordapi.feature.extractor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

class InMemoryExtractorStoreTest {

    @Test
    @DisplayName("save populates id and createdAt when missing")
    void saveFillsDefaults() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = new Extractor();
        row.setOwnerUserId(100L);
        row.setShortId("x");
        row.setLabel("X");
        store.save(row).block();
        assertThat(row.getId().equals(new UUID(0L, 0L)), is(false));
        assertThat(row.getCreatedAt().toEpochMilli() > 0, is(true));
    }

    @Test
    @DisplayName("findById returns empty when caller cannot use the extractor")
    void findByIdRespectsVisibility() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = new Extractor();
        row.setOwnerUserId(100L);
        row.setShortId("x");
        row.setVisibility(Extractor.Visibility.PRIVATE);
        store.save(row).block();
        assertThat(store.findById(row.getId(), 100L, null).blockOptional().isPresent(), is(true));
        assertThat(store.findById(row.getId(), 200L, null).blockOptional().isPresent(), is(false));
    }

    @Test
    @DisplayName("findByShortId prefers owner-private over guild over public")
    void shortIdPreference() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        // Three extractors with the same shortId, different visibilities.
        Extractor priv = new Extractor();
        priv.setOwnerUserId(100L);
        priv.setShortId("dmg");
        priv.setLabel("private dmg");
        priv.setVisibility(Extractor.Visibility.PRIVATE);
        Extractor guild = new Extractor();
        guild.setOwnerUserId(200L);
        guild.setShortId("dmg");
        guild.setLabel("guild dmg");
        guild.setVisibility(Extractor.Visibility.GUILD);
        guild.setGuildId(999L);
        Extractor pub = new Extractor();
        pub.setOwnerUserId(300L);
        pub.setShortId("dmg");
        pub.setLabel("public dmg");
        pub.setVisibility(Extractor.Visibility.PUBLIC);
        store.save(priv).block();
        store.save(guild).block();
        store.save(pub).block();

        Extractor pick = store.findByShortId("dmg", 100L, 999L).block();
        assertThat(pick, is(equalTo(priv)));
        Extractor pickGuild = store.findByShortId("dmg", 200L, 999L).block();
        assertThat(pickGuild, is(equalTo(guild)));
        Extractor pickPub = store.findByShortId("dmg", 999L, null).block();
        assertThat(pickPub, is(equalTo(pub)));
    }

    @Test
    @DisplayName("findVisible returns sorted accessible extractors")
    void findVisibleSorted() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        for (String label : new String[] {"zeta", "alpha", "mu"}) {
            Extractor row = new Extractor();
            row.setOwnerUserId(100L);
            row.setShortId(label);
            row.setLabel(label);
            row.setVisibility(Extractor.Visibility.PRIVATE);
            store.save(row).block();
        }
        List<Extractor> rows = store.findVisible(100L, null).collectList().block();
        assertThat(rows.size(), is(equalTo(3)));
        assertThat(rows.get(0).getLabel(), is(equalTo("alpha")));
        assertThat(rows.get(2).getLabel(), is(equalTo("zeta")));
    }

    @Test
    @DisplayName("deleteById refuses non-owners")
    void deleteRefusesNonOwner() {
        InMemoryExtractorStore store = InMemoryExtractorStore.of();
        Extractor row = new Extractor();
        row.setOwnerUserId(100L);
        store.save(row).block();
        assertThat(store.deleteById(row.getId(), 200L).block(), is(false));
        assertThat(store.deleteById(row.getId(), 100L).block(), is(true));
    }

}
