package com.subdual.research_service.discovery.ranking;

import com.subdual.research_service.research.model.DiscoveredSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDeduplicatorTest {

    @Test
    @DisplayName("Should deduplicate sources with equivalent URLs and retain highest relevance")
    void shouldDeduplicateEquivalentUrls() {
        DiscoveredSource s1 = new DiscoveredSource(
                "https://example.com/team/alice/",
                "Alice",
                "SEARCH_RESULT",
                Instant.now(),
                0.60
        );

        DiscoveredSource s2 = new DiscoveredSource(
                "http://example.com/team/alice",
                "Alice Wonder",
                "SEARCH_RESULT",
                Instant.now(),
                0.90
        );

        DiscoveredSource s3 = new DiscoveredSource(
                "https://example.com/blog",
                "Blog",
                "SEARCH_RESULT",
                Instant.now(),
                0.50
        );

        var result = SourceDeduplicator.deduplicate(List.of(s1, s2, s3));

        assertThat(result.deduplicated()).hasSize(2);
        assertThat(result.duplicatesRemovedCount()).isEqualTo(1);
        assertThat(result.deduplicated().get(0).relevance()).isEqualTo(0.90);
    }
}
