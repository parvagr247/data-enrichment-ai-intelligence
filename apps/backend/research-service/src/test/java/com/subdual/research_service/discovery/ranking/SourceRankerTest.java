package com.subdual.research_service.discovery.ranking;

import com.subdual.research_service.discovery.model.QueryIntent;
import com.subdual.research_service.research.model.DiscoveredSource;
import com.subdual.research_service.research.model.EntityType;
import com.subdual.research_service.research.model.ResearchTarget;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceRankerTest {

    @Test
    @DisplayName("Should rank official domain higher than generic search result")
    void shouldRankOfficialDomainHigher() {
        ResearchTarget target = new ResearchTarget(
                "https://spring.io",
                "https://spring.io",
                "id-1",
                EntityType.ORGANIZATION,
                "Spring"
        );

        DiscoveredSource generic = new DiscoveredSource(
                "https://some-aggregator.com/spring-review",
                "Spring Review",
                "SEARCH_RESULT",
                Instant.now(),
                0.70
        );

        DiscoveredSource official = new DiscoveredSource(
                "https://spring.io/projects/spring-framework",
                "Spring Framework Home",
                "OFFICIAL_WEBSITE",
                Instant.now(),
                0.70
        );

        List<DiscoveredSource> ranked = SourceRanker.rankSources(
                List.of(generic, official),
                target,
                QueryIntent.ORGANIZATION
        );

        assertThat(ranked).hasSize(2);
        assertThat(ranked.get(0).url()).isEqualTo("https://spring.io/projects/spring-framework");
    }

    @Test
    @DisplayName("Should boost source containing entity name in title and snippet")
    void shouldBoostMatchingEntityName() {
        ResearchTarget target = new ResearchTarget(
                "https://example.com/alice",
                "https://example.com/alice",
                "id-2",
                EntityType.PERSON,
                "Alice Wonder"
        );

        DiscoveredSource noNameMatch = new DiscoveredSource(
                "https://tech-news.org/article",
                "Industry Trends",
                "NEWS",
                Instant.now(),
                0.60,
                "General discussion about trends."
        );

        DiscoveredSource nameMatch = new DiscoveredSource(
                "https://tech-news.org/interview-alice",
                "Alice Wonder on Modern Systems",
                "NEWS",
                Instant.now(),
                0.60,
                "Alice Wonder discusses systems architecture."
        );

        List<DiscoveredSource> ranked = SourceRanker.rankSources(
                List.of(noNameMatch, nameMatch),
                target,
                QueryIntent.ROLE
        );

        assertThat(ranked.get(0).url()).isEqualTo("https://tech-news.org/interview-alice");
        assertThat(ranked.get(0).relevance()).isGreaterThan(ranked.get(1).relevance());
    }
}
